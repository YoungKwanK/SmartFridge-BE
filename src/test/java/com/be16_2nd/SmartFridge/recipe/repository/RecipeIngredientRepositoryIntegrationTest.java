package com.be16_2nd.SmartFridge.recipe.repository;

import com.be16_2nd.SmartFridge.recipe.domain.Ingredient;
import com.be16_2nd.SmartFridge.recipe.domain.Recipe;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/*
 * 재료 겹침 추천 쿼리를 실제 DB에서 확인한다 (정렬 순서, 기본 양념 제외, 개수 제한)
 * 실제 데이터와 겹치지 않는 재료 이름과 원본 번호를 쓰고, 테스트가 끝나면 롤백된다.
 */
@SpringBootTest
@Transactional
class RecipeIngredientRepositoryIntegrationTest {

    @Autowired RecipeIngredientRepository recipeIngredientRepository;
    @Autowired IngredientRepository ingredientRepository;
    @Autowired RecipeRepository recipeRepository;
    @Autowired EntityManager em;

    Ingredient a, b, c, d, pantry;

    @BeforeEach
    void setUp() {
        a = ingredient("추천테스트재료a", false);
        b = ingredient("추천테스트재료b", false);
        c = ingredient("추천테스트재료c", false);
        d = ingredient("추천테스트재료d", false);
        pantry = ingredient("추천테스트양념", true);
    }

    @Test
    void 부족한_재료가_적은_순_그다음_가진_재료가_많은_순_그다음_임박_재료가_많은_순으로_찾는다() {
        Long allOwned = recipe(9_800_001L, a, b);              // 부족 0, 보유 2
        Long oneMissingThree = recipe(9_800_002L, a, b, c, d); // 부족 1, 보유 3
        Long oneMissingOne = recipe(9_800_003L, a, d);         // 부족 1, 보유 1
        Long oneMissingOneExpiring = recipe(9_800_004L, c, d); // 부족 1, 보유 1, 임박 1
        Long noMatch = recipe(9_800_005L, d);                  // 보유 0 → 빠짐
        Long onlyPantry = recipe(9_800_006L, pantry, a);       // 양념은 세지 않아 부족 0, 보유 1
        flushAndClear();

        List<RecipeMatch> result = recipeIngredientRepository.findBestMatches(
                Set.of(a.getId(), b.getId(), c.getId()), Set.of(c.getId()), Limit.of(10));

        assertThat(result)
                .extracting(RecipeMatch::recipeId, RecipeMatch::matchedCount, RecipeMatch::expiringCount, RecipeMatch::totalCount)
                .containsExactly(
                        tuple(allOwned, 2L, 0L, 2L),
                        tuple(onlyPantry, 1L, 0L, 1L),
                        tuple(oneMissingThree, 3L, 1L, 4L),
                        tuple(oneMissingOneExpiring, 1L, 1L, 2L),
                        tuple(oneMissingOne, 1L, 0L, 2L));
        assertThat(result).extracting(RecipeMatch::recipeId).doesNotContain(noMatch);
    }

    @Test
    void 개수_제한만큼만_돌려준다() {
        recipe(9_800_011L, a);
        recipe(9_800_012L, a, b);
        recipe(9_800_013L, a, c);
        flushAndClear();

        List<RecipeMatch> result = recipeIngredientRepository.findBestMatches(
                Set.of(a.getId()), Set.of(-1L), Limit.of(2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).missingCount()).isZero();
    }

    private Ingredient ingredient(String name, boolean isPantry) {
        return ingredientRepository.save(Ingredient.builder().name(name).pantry(isPantry).build());
    }

    private Long recipe(long sourceId, Ingredient... ingredients) {
        Recipe recipe = Recipe.builder().sourceId(sourceId).name("추천 테스트 " + sourceId).build();
        LinkedHashMap<Ingredient, String> amounts = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            amounts.put(ingredient, "1개");
        }
        recipe.replaceIngredients(amounts);
        return recipeRepository.save(recipe).getId();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}

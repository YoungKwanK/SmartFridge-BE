package com.be16_2nd.SmartFridge.recipe.repository;

import com.be16_2nd.SmartFridge.recipe.domain.Ingredient;
import com.be16_2nd.SmartFridge.recipe.domain.Recipe;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
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

    @Test
    void 고른_재료를_많이_쓰는_순_그다음_더_필요한_재료가_적은_순으로_찾는다() {
        // 고른 재료 a, b (b 는 냉장고에 없다고 치고), 냉장고 재료 c
        Long bothSelected = recipe(9_800_021L, a, b, d);          // 고른 2, 더 필요 1(d)
        Long oneSelectedReady = recipe(9_800_022L, a, c);         // 고른 1, 더 필요 0
        Long oneSelectedMissing = recipe(9_800_023L, b, d);       // 고른 1, 더 필요 1
        Long notSelected = recipe(9_800_024L, c, d);              // 고른 0 → 빠짐
        Long withPantry = recipe(9_800_025L, pantry, b);          // 양념은 세지 않아 고른 1, 더 필요 0
        flushAndClear();

        List<RecipePickMatch> result = recipeIngredientRepository.findBySelectedIngredients(
                Set.of(a.getId(), b.getId()), Set.of(a.getId(), b.getId(), c.getId()), Limit.of(10));

        // 실제 레시피 데이터는 이 테스트 재료를 쓰지 않으므로 여기서 만든 레시피만 나온다
        assertThat(result)
                .extracting(RecipePickMatch::recipeId, RecipePickMatch::selectedCount,
                        RecipePickMatch::availableCount, RecipePickMatch::totalCount)
                .containsExactly(
                        tuple(bothSelected, 2L, 2L, 3L),
                        tuple(oneSelectedReady, 1L, 2L, 2L),
                        tuple(withPantry, 1L, 1L, 1L),
                        tuple(oneSelectedMissing, 1L, 1L, 2L));
        assertThat(result).extracting(RecipePickMatch::recipeId).doesNotContain(notSelected);
    }

    @Test
    void 레시피_이름은_띄어쓰기를_무시하고_짧은_이름부터_찾는다() {
        Long longer = recipe(9_800_031L, "검색테스트 김치 볶음밥", a);
        Long shorter = recipe(9_800_032L, "검색테스트김치볶음밥", a);
        recipe(9_800_033L, "검색테스트 된장찌개", a);
        flushAndClear();

        Slice<Recipe> first = recipeRepository.searchByName("검색테스트김치볶음", PageRequest.of(0, 1));
        Slice<Recipe> second = recipeRepository.searchByName("검색테스트김치볶음", PageRequest.of(1, 1));

        assertThat(first.getContent()).extracting(Recipe::getId).containsExactly(shorter);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.getContent()).extracting(Recipe::getId).containsExactly(longer);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void 레시피_이름_검색어의_퍼센트와_밑줄은_글자_그대로_찾는다() {
        Long percent = recipe(9_800_041L, "검색테스트100%주스", a);
        recipe(9_800_042L, "검색테스트1000주스", a);
        flushAndClear();

        Slice<Recipe> result = recipeRepository.searchByName("검색테스트100!%", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Recipe::getId).containsExactly(percent);
    }

    @Test
    void 재료_이름_검색은_기본_양념을_빼고_짧은_이름부터_찾는다() {
        ingredient("추천테스트양념a", true);

        List<Ingredient> result = ingredientRepository.searchByName("추천테스트", Limit.of(10));

        assertThat(result).extracting(Ingredient::getName)
                .startsWith("추천테스트재료a", "추천테스트재료b", "추천테스트재료c", "추천테스트재료d")
                .doesNotContain("추천테스트양념", "추천테스트양념a");
    }

    private Ingredient ingredient(String name, boolean isPantry) {
        return ingredientRepository.save(Ingredient.builder().name(name).pantry(isPantry).build());
    }

    private Long recipe(long sourceId, Ingredient... ingredients) {
        return recipe(sourceId, "추천 테스트 " + sourceId, ingredients);
    }

    private Long recipe(long sourceId, String name, Ingredient... ingredients) {
        Recipe recipe = Recipe.builder().sourceId(sourceId).name(name).build();
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

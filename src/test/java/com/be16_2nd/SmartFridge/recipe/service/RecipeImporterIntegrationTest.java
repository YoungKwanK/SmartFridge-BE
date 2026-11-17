package com.be16_2nd.SmartFridge.recipe.service;

import com.be16_2nd.SmartFridge.recipe.domain.Ingredient;
import com.be16_2nd.SmartFridge.recipe.domain.Recipe;
import com.be16_2nd.SmartFridge.recipe.domain.RecipeIngredient;
import com.be16_2nd.SmartFridge.recipe.domain.RecipeStep;
import com.be16_2nd.SmartFridge.recipe.repository.IngredientRepository;
import com.be16_2nd.SmartFridge.recipe.repository.RecipeRepository;
import com.be16_2nd.SmartFridge.recipe.source.RecipeSourceRow;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/*
 * 레시피 적재가 실제 DB에 맞게 들어가는지 확인 (새로 넣기, 다시 넣을 때 덮어쓰기, 재료 마스터 공유)
 * 원본 번호는 실제 데이터와 겹치지 않게 큰 값을 쓰고, 테스트가 끝나면 롤백된다.
 */
@SpringBootTest
@Transactional
class RecipeImporterIntegrationTest {

    @Autowired RecipeImporter importer;
    @Autowired RecipeRepository recipeRepository;
    @Autowired IngredientRepository ingredientRepository;
    @Autowired EntityManager em;

    static final long SEQ_A = 9_900_001L;
    static final long SEQ_B = 9_900_002L;

    @Test
    void 레시피와_단계와_정규화된_재료를_넣는다() {
        RecipeImporter.PageResult result = importer.importPage(List.of(row(SEQ_A, "새우 두부 계란찜",
                "연두부 75g(3/4모), 칵테일새우 20g, 달걀 30g(1/2개), 다진 대파 5g, 설탕 5g\n고명\n달걀노른자 약간, 알룰로스 3g",
                "1. 새우를 데친다.a", "2. 찐다.")));
        flushAndClear();

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.unknownNames()).containsExactlyInAnyOrder("연두부", "알룰로스");

        Recipe recipe = recipeRepository.findBySourceId(SEQ_A).orElseThrow();
        assertThat(recipe.getSteps()).extracting(RecipeStep::getStepOrder, RecipeStep::getDescription)
                .containsExactly(tuple(1, "새우를 데친다."),
                        tuple(2, "찐다."));
        // 달걀과 달걀노른자는 같은 "계란"이라 하나만 남고 처음 분량을 쓴다
        assertThat(recipe.getIngredients())
                .extracting(ri -> ri.getIngredient().getName(), RecipeIngredient::getAmount)
                .containsExactlyInAnyOrder(
                        tuple("연두부", "75g(3/4모)"),
                        tuple("새우", "20g"),
                        tuple("계란", "30g(1/2개)"),
                        tuple("대파", "5g"),
                        tuple("설탕", "5g"),
                        tuple("알룰로스", "3g"));
        assertThat(ingredient("설탕").isPantry()).isTrue();
        assertThat(ingredient("새우").isPantry()).isFalse();
    }

    @Test
    void 같은_원본을_다시_넣으면_새로_만들지_않고_덮어쓴다() {
        importer.importPage(List.of(row(SEQ_A, "계란찜", "달걀 2개, 대파 10g, 소금 약간",
                "1. 푼다.", "2. 찐다.", "3. 담는다.")));
        flushAndClear();
        Long id = recipeRepository.findBySourceId(SEQ_A).orElseThrow().getId();

        RecipeImporter.PageResult result = importer.importPage(List.of(row(SEQ_A, "부드러운 계란찜",
                "달걀 3개, 양파 20g", "1. 잘 푼다.", "2. 찐다.")));
        flushAndClear();

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        Recipe recipe = recipeRepository.findBySourceId(SEQ_A).orElseThrow();
        assertThat(recipe.getId()).isEqualTo(id);
        assertThat(recipe.getName()).isEqualTo("부드러운 계란찜");
        assertThat(recipe.getSteps()).extracting(RecipeStep::getDescription).containsExactly("잘 푼다.", "찐다.");
        assertThat(recipe.getIngredients())
                .extracting(ri -> ri.getIngredient().getName(), RecipeIngredient::getAmount)
                .containsExactlyInAnyOrder(
                        tuple("계란", "3개"),
                        tuple("양파", "20g"));
    }

    @Test
    void 여러_레시피가_같은_재료를_함께_쓴다() {
        importer.importPage(List.of(
                row(SEQ_A, "계란찜", "달걀 2개", "1. 찐다."),
                row(SEQ_B, "계란말이", "계란 3개, 대파 10g", "1. 만다.")));
        flushAndClear();

        Ingredient egg = ingredient("계란");
        long recipesUsingEgg = recipeRepository.findAllBySourceIdIn(List.of(SEQ_A, SEQ_B)).stream()
                .flatMap(r -> r.getIngredients().stream())
                .filter(ri -> ri.getIngredient().getId().equals(egg.getId()))
                .count();
        assertThat(recipesUsingEgg).isEqualTo(2);
        assertThat(ingredientRepository.findAllByNameIn(List.of("계란"))).hasSize(1);
    }

    private Ingredient ingredient(String name) {
        return ingredientRepository.findAllByNameIn(List.of(name)).get(0);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    // 공공데이터 응답 한 행과 같은 모양으로 만든다
    private static RecipeSourceRow row(long seq, String name, String ingredientsText, String... steps) {
        Map<String, String> raw = new HashMap<>();
        raw.put("RCP_SEQ", String.valueOf(seq));
        raw.put("RCP_NM", name);
        raw.put("RCP_PAT2", "반찬");
        raw.put("RCP_WAY2", "찌기");
        raw.put("RCP_PARTS_DTLS", ingredientsText);
        for (int i = 0; i < steps.length; i++) {
            raw.put(String.format("MANUAL%02d", i + 1), steps[i]);
        }
        return RecipeSourceRow.from(raw);
    }
}

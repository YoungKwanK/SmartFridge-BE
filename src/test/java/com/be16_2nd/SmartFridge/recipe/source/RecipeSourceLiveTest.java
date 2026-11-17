package com.be16_2nd.SmartFridge.recipe.source;

import com.be16_2nd.SmartFridge.recipe.ingredient.IngredientDictionary;
import com.be16_2nd.SmartFridge.recipe.ingredient.IngredientParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 식품안전나라 API를 실제로 불러 응답 → 레시피 행 → 재료 파싱까지 이어지는지 확인한다.
 * 외부 호출이라 평소에는 건너뛰고, RECIPE_SOURCE_LIVE=true 일 때만 돈다.
 * FOOD_SAFETY_API_KEY 가 없으면 시험용 키(sample)로 몇 건만 받는다.
 *
 * <pre>RECIPE_SOURCE_LIVE=true ./gradlew test --tests '*RecipeSourceLiveTest'</pre>
 */
@EnabledIfEnvironmentVariable(named = "RECIPE_SOURCE_LIVE", matches = "true")
class RecipeSourceLiveTest {

    @Test
    void 실제_응답을_레시피와_재료로_바꿀_수_있다() {
        RecipeSourceProperties properties = new RecipeSourceProperties(
                "http://openapi.foodsafetykorea.go.kr/api", System.getenv("FOOD_SAFETY_API_KEY"), 5, Duration.ofSeconds(30));
        RecipeSourceClient client = new RecipeSourceClient(properties);
        IngredientDictionary dictionary = new IngredientDictionary();

        RecipeSourceClient.RecipeSourcePage page = client.fetch(1, 5);
        List<RecipeSourceRow> rows = page.rows().stream().map(RecipeSourceRow::from).toList();

        assertThat(page.totalCount()).isGreaterThan(1000);
        assertThat(rows).isNotEmpty().allSatisfy(row -> {
            assertThat(row.name()).isNotBlank();
            assertThat(row.steps()).isNotEmpty();
            assertThat(IngredientParser.parse(row.ingredientsText())).isNotEmpty();
        });
        rows.forEach(row -> System.out.println(row.name() + " → " + IngredientParser.parse(row.ingredientsText()).stream()
                .map(item -> dictionary.resolve(item.name()).name() + "(" + item.amount() + ")")
                .toList()));
    }
}

package com.be16_2nd.SmartFridge.recipe.service;

import com.be16_2nd.SmartFridge.common.ai.AiClient;
import com.be16_2nd.SmartFridge.common.ai.AiConfig;
import com.be16_2nd.SmartFridge.common.ai.AiProperties;
import com.be16_2nd.SmartFridge.recipe.dto.RecipeAiRecommendResDto;
import com.be16_2nd.SmartFridge.recipe.dto.RecipeRecommendResDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실제 Claude API 로 재랭킹 응답 형식(구조화 출력)을 확인하는 테스트 (호출마다 비용이 든다)
 * - ANTHROPIC_API_KEY 환경변수가 있을 때만 돈다
 * - 후보는 DB 없이 직접 만든다
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = "sk-ant-.+")
class RecipeAiRecommendLiveTest {

    @Test
    void 실제_AI로_후보를_다시_고르고_이유를_붙인다() {
        AiProperties properties = new AiProperties(
                System.getenv("ANTHROPIC_API_KEY"), "claude-haiku-5-5", 16000L, Duration.ofSeconds(60), 2);
        AiClient aiClient = new AiClient(new AiConfig().anthropicClient(properties), properties);
        RecipeRecommendService recipeRecommendService = mock(RecipeRecommendService.class);
        when(recipeRecommendService.recommend(1L, RecipeAiRecommendService.CANDIDATE_SIZE)).thenReturn(List.of(
                recipe(1L, "계란말이", "반찬", List.of("계란", "대파"), List.of(), List.of("당근")),
                recipe(2L, "두부된장찌개", "국&찌개", List.of("두부", "대파"), List.of("두부"), List.of("애호박")),
                recipe(3L, "애호박전", "반찬", List.of("계란"), List.of(), List.of("애호박")),
                recipe(4L, "두부조림", "반찬", List.of("두부"), List.of("두부"), List.of()),
                recipe(5L, "계란볶음밥", "일품", List.of("계란", "대파"), List.of(), List.of("햄"))));

        RecipeAiRecommendResDto result = new RecipeAiRecommendService(recipeRecommendService, aiClient)
                .recommend(1L, 3);

        result.recipes().forEach(item -> System.out.printf("[AI 추천] %s - %s%n", item.recipe().name(), item.reason()));
        assertThat(result.aiRanked()).isTrue();
        assertThat(result.recipes()).hasSize(3);
        assertThat(result.recipes().get(0).reason()).isNotBlank();
    }

    private static RecipeRecommendResDto recipe(Long id, String name, String dishType, List<String> matched,
                                                List<String> expiring, List<String> missing) {
        return new RecipeRecommendResDto(id, name, dishType, "볶기", null, 200,
                matched.size(), matched.size() + missing.size(), matched,
                missing.stream().map(m -> new RecipeRecommendResDto.MissingIngredient(m, "1개")).toList(),
                expiring);
    }
}

package com.be16_2nd.SmartFridge.common.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Claude API 를 호출하는 확인용 테스트 (호출마다 비용이 든다)
 * - ANTHROPIC_API_KEY 환경변수가 있을 때만 돈다. 없으면 건너뛴다
 * - Redis 등 다른 인프라 없이 돌도록 스프링 컨텍스트를 띄우지 않고 직접 만든다
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = "sk-ant-.+")
class AiClientLiveTest {

    private AiClient aiClient;

    record Recipe(String name, List<String> usedIngredients, List<String> steps, int cookingMinutes) {}
    record RecipeRecommendation(List<Recipe> recipes) {}

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                System.getenv("ANTHROPIC_API_KEY"), "claude-haiku-5-5", 16000L, Duration.ofSeconds(60), 2);
        aiClient = new AiClient(new AiConfig().anthropicClient(properties), properties);
    }

    @Test
    void 문자열로_받기() {
        String text = aiClient.generate(AiPrompt.builder()
                .system("너는 요리 도우미야. 한 문장으로 짧게 답해.")
                .user("계란과 양파로 만들 수 있는 요리 하나만 알려줘.")
                .effort(AiEffort.LOW)
                .build());

        System.out.println("[문자열 응답] " + text);
        assertThat(text).isNotBlank();
    }

    @Test
    void DTO로_받기() {
        RecipeRecommendation result = aiClient.generate(
                AiPrompt.of(
                        "너는 냉장고 재료로 만들 수 있는 한국 가정식을 추천하는 셰프야. 레시피 2개를 추천해.",
                        "냉장고 재료: 계란, 양파, 대파, 김치, 두부"),
                RecipeRecommendation.class);

        result.recipes().forEach(r -> System.out.printf("[DTO 응답] %s (%d분) 재료=%s 단계=%d개%n",
                r.name(), r.cookingMinutes(), r.usedIngredients(), r.steps().size()));
        assertThat(result.recipes()).isNotEmpty();
        assertThat(result.recipes().get(0).steps()).isNotEmpty();
    }
}

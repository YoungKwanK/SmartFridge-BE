package com.be16_2nd.SmartFridge.recipe.source;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 식품안전나라 조리식품 레시피 DB(COOKRCP01) 설정 (application-local.yml / application-prod.yml 의 recipe.source.*)
 * - apiKey 는 환경변수 FOOD_SAFETY_API_KEY 로 넣는다. 없으면 "sample" (몇 건만 내려주는 시험용 키)
 * - pageSize 는 한 번에 받을 건수 (API 최대 1000)
 */
@ConfigurationProperties(prefix = "recipe.source")
public record RecipeSourceProperties(
        String baseUrl,
        String apiKey,
        int pageSize,
        Duration timeout
) {
    public static final String SAMPLE_KEY = "sample";

    public boolean isSampleKey() {
        return apiKey == null || apiKey.isBlank() || SAMPLE_KEY.equals(apiKey);
    }

    public String effectiveApiKey() {
        return isSampleKey() ? SAMPLE_KEY : apiKey;
    }
}

package com.be16_2nd.SmartFridge.common.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Claude API 공통 설정 (application.yml 의 ai.anthropic.*)
 * - apiKey 는 환경변수 ANTHROPIC_API_KEY 로만 주입한다
 * - model / maxTokens 는 기능별 요청(AiPrompt)에서 따로 주지 않을 때 쓰는 기본값
 */
@ConfigurationProperties(prefix = "ai.anthropic")
public record AiProperties(
        String apiKey,
        String model,
        long maxTokens,
        Duration timeout,
        int maxRetries
) {
}

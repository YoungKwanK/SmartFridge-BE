package com.be16_2nd.SmartFridge.common.ai;

import lombok.Builder;

/**
 * AI 한 번 호출에 필요한 입력
 *
 * <pre>
 * AiPrompt.of(SYSTEM_PROMPT, "냉장고 재료: 계란, 양파")
 * AiPrompt.builder().system(...).user(...).effort(AiEffort.LOW).build()
 * </pre>
 *
 * @param system    기능별 역할·규칙 (고정 문구로 두면 프롬프트 캐싱에 유리하다)
 * @param user      요청마다 달라지는 입력
 * @param effort    null 이면 모델 기본값
 * @param maxTokens null 이면 ai.anthropic.max-tokens
 * @param model     null 이면 ai.anthropic.model
 */
@Builder
public record AiPrompt(
        String system,
        String user,
        AiEffort effort,
        Long maxTokens,
        String model
) {
    public AiPrompt {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("AI 요청 내용(user)은 비어 있을 수 없습니다.");
        }
    }

    public static AiPrompt of(String system, String user) {
        return AiPrompt.builder().system(system).user(user).build();
    }
}

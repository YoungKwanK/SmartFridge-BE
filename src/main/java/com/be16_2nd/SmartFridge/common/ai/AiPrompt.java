package com.be16_2nd.SmartFridge.common.ai;

import lombok.Builder;

import java.util.List;

/**
 * AI 한 번 호출에 필요한 입력
 *
 * <pre>
 * AiPrompt.of(SYSTEM_PROMPT, "냉장고 재료: 계란, 양파")
 * AiPrompt.builder().system(...).user(...).effort(AiEffort.LOW).build()
 * AiPrompt.builder().system(...).user("이 사진에서 식품을 뽑아 줘").images(List.of(image)).build()
 * </pre>
 *
 * @param system    기능별 역할·규칙 (고정 문구로 두면 프롬프트 캐싱에 유리하다)
 * @param user      요청마다 달라지는 입력
 * @param effort    null 이면 모델 기본값
 * @param maxTokens null 이면 ai.anthropic.max-tokens
 * @param model     null 이면 ai.anthropic.model
 * @param images    user 글 앞에 같이 보내는 이미지. null 이면 글만 보낸다
 */
@Builder
public record AiPrompt(
        String system,
        String user,
        AiEffort effort,
        Long maxTokens,
        String model,
        List<AiImage> images
) {
    public AiPrompt {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("AI 요청 내용(user)은 비어 있을 수 없습니다.");
        }
        images = images == null ? List.of() : List.copyOf(images);
    }

    public static AiPrompt of(String system, String user) {
        return AiPrompt.builder().system(system).user(user).build();
    }
}

package com.be16_2nd.SmartFridge.common.ai;

import com.anthropic.models.messages.OutputConfig;

/**
 * 모델이 얼마나 깊게 생각할지 (낮을수록 빠르고 저렴하다)
 * - LOW: 분류·짧은 문구 생성처럼 단순한 작업
 * - MEDIUM: Haiku 기본값. 레시피 추천처럼 일반적인 작업
 * - HIGH: 정확도가 더 중요한 작업
 */
public enum AiEffort {
    LOW(OutputConfig.Effort.LOW),
    MEDIUM(OutputConfig.Effort.MEDIUM),
    HIGH(OutputConfig.Effort.HIGH);

    private final OutputConfig.Effort sdkEffort;

    AiEffort(OutputConfig.Effort sdkEffort) {
        this.sdkEffort = sdkEffort;
    }

    OutputConfig.Effort toSdk() {
        return sdkEffort;
    }
}

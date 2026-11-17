package com.be16_2nd.SmartFridge.agent.domain;

import com.be16_2nd.SmartFridge.food.domain.FoodResult;

/**
 * AI 에이전트가 제안할 수 있는 작업 종류. 확인 버튼을 눌러야 실행된다.
 */
public enum AgentActionType {
    FINISH_CONSUMED(FoodResult.소비),   // 다 먹었어요 (기록 남김)
    FINISH_DISCARDED(FoodResult.폐기),  // 버렸어요 (기록 남김)
    DELETE(null);                       // 잘못 등록했어요 (기록 없이 삭제, 식품 1개만)

    private final FoodResult finishResult;

    AgentActionType(FoodResult finishResult) {
        this.finishResult = finishResult;
    }

    public FoodResult getFinishResult() {
        return finishResult;
    }

    public static AgentActionType finishOf(FoodResult result) {
        if (result == null) {
            throw new IllegalArgumentException("처리 결과(소비 또는 폐기)를 선택해 주세요.");
        }
        return result == FoodResult.소비 ? FINISH_CONSUMED : FINISH_DISCARDED;
    }
}

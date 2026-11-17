package com.be16_2nd.SmartFridge.agent.domain;

/**
 * 제안의 상태. PENDING에서만 다른 상태로 바뀌고, 한 번 바뀌면 다시 바뀌지 않는다.
 */
public enum AgentActionStatus {
    PENDING,    // 확인 기다리는 중
    CONFIRMED,  // 사용자가 확인해서 실행함
    CANCELLED,  // 사용자가 취소함
    EXPIRED,    // 유효 시간(10분)이 지나 실행할 수 없음
    FAILED      // 확인했지만 다시 검사하다 실패함 (식품이 이미 없거나 권한이 바뀜)
}

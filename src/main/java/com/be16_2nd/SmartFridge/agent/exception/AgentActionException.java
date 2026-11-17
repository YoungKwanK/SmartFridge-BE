package com.be16_2nd.SmartFridge.agent.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 제안을 확인·취소할 수 없는 상태일 때 (이미 처리됨 409, 유효 시간 지남 410).
 */
@Getter
public class AgentActionException extends RuntimeException {
    private final HttpStatus status;

    public AgentActionException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static AgentActionException alreadyProcessed() {
        return new AgentActionException(HttpStatus.CONFLICT, "이미 처리했거나 취소한 제안이에요.");
    }

    public static AgentActionException expired() {
        return new AgentActionException(HttpStatus.GONE, "확인 시간이 지났어요. 다시 요청해 주세요.");
    }
}

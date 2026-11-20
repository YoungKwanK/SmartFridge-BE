package com.be16_2nd.SmartFridge.agent.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 에이전트 채팅을 쓸 수 없는 상태 (하루 사용 횟수 초과 429).
 */
@Getter
public class AgentChatException extends RuntimeException {
    private final HttpStatus status;

    public AgentChatException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static AgentChatException dailyLimitExceeded(int limit) {
        return new AgentChatException(HttpStatus.TOO_MANY_REQUESTS,
                "오늘은 AI 도우미를 " + limit + "번 모두 사용했어요. 내일 다시 이용해 주세요.");
    }
}

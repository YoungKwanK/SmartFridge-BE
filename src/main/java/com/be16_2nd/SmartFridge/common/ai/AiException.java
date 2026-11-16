package com.be16_2nd.SmartFridge.common.ai;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * AI 호출 실패. 원인별로 클라이언트에 내려줄 상태 코드를 같이 들고 있다
 * - 503: 요청이 몰리거나(429) AI 서버가 일시적으로 불안정할 때 → 잠시 후 재시도 가능
 * - 502: 응답이 잘렸거나 거절되는 등 정상 응답을 받지 못했을 때
 */
@Getter
public class AiException extends RuntimeException {

    private final HttpStatus status;

    public AiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public AiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}

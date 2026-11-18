package com.be16_2nd.SmartFridge.receipt.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 영수증 인식을 쓸 수 없는 상태 (하루 사용 횟수 초과 429).
 */
@Getter
public class ReceiptException extends RuntimeException {
    private final HttpStatus status;

    public ReceiptException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ReceiptException dailyLimitExceeded(int limit) {
        return new ReceiptException(HttpStatus.TOO_MANY_REQUESTS,
                "오늘은 영수증 인식을 " + limit + "번 모두 사용했어요. 내일 다시 이용해 주세요.");
    }
}

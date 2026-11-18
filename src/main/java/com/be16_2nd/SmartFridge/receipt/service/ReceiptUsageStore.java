package com.be16_2nd.SmartFridge.receipt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 영수증 인식 사용량: 회원별로 하루 {@value #DAILY_LIMIT}번까지. 날짜가 바뀌면 새로 센다.
 */
@Component
@RequiredArgsConstructor
public class ReceiptUsageStore {

    public static final int DAILY_LIMIT = 20;
    private static final Duration USAGE_TTL = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;

    /** 한 번 사용으로 세고, 오늘 쓴 횟수를 돌려준다 */
    public long increaseUsage(UUID memberId) {
        String key = usageKey(memberId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, USAGE_TTL);
        }
        return count == null ? 0L : count;
    }

    private static String usageKey(UUID memberId) {
        return "receipt:usage:" + memberId + ":" + LocalDate.now();
    }
}

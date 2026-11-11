package com.be16_2nd.SmartFridge.notification.service;

import com.be16_2nd.SmartFridge.common.dto.SseMessageDTO;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
//@RequiredArgsConstructor
public class NotificationPublisher {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public NotificationPublisher(@Qualifier("ssePubSub") RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(Long fridgeId, String receiverEmail, String contents, String type) {
        publish(fridgeId, receiverEmail, contents, type, null);
    }

    // 채팅 알림: 어느 방의 메시지인지 함께 보낸다
    public void publish(Long fridgeId, String receiverEmail, String contents, String type, Long roomId) {
        SseMessageDTO sseMessageDTO = SseMessageDTO.builder()
                .type(type)
                .fridgeId(fridgeId)
                .receiverEmail(receiverEmail)
                .contents(contents)
                .roomId(roomId)
                .build();

        String data;
        try {
            data = objectMapper.writeValueAsString(sseMessageDTO);
            log.info("Redis 채널로 알림 발행. 수신자: {}", receiverEmail);
            redisTemplate.convertAndSend("sse-channel", data);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }
}

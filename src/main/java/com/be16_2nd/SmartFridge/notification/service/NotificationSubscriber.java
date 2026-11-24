package com.be16_2nd.SmartFridge.notification.service;

import com.be16_2nd.SmartFridge.common.dto.SseMessageDTO;
import com.be16_2nd.SmartFridge.common.service.SseEmitterRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSubscriber implements MessageListener {

    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SseMessageDTO sseMessageDTO = objectMapper.readValue(message.getBody(), SseMessageDTO.class);
            // SSE 레지스트리 키 = 회원 id 문자열 (JWT subject)
            String receiverKey = String.valueOf(sseMessageDTO.getReceiverId());
            log.info("Redis 구독 메시지 수신. 수신자: {}", receiverKey);

            SseEmitter sseEmitter = sseEmitterRegistry.getEmitter(receiverKey);

            if (sseEmitter != null) {
                try {
                    String eventData = objectMapper.writeValueAsString(sseMessageDTO);
                    sseEmitter.send(SseEmitter.event()
                            .name(sseMessageDTO.getType())
                            .data(eventData));
                    log.info("실시간 알림 전송 완료: {}", receiverKey);
                    log.info("실시간 알림 전송 내용: {}", sseMessageDTO);

                } catch (IOException e) {
                    // 전송 중 오류 발생 시 Emitter 제거
                    log.error("SSE 전송 오류. Emitter 제거. 수신자: {}", receiverKey, e);
                    sseEmitterRegistry.removeEmitter(receiverKey);
                }
            } else {
                log.info("수신자 {}가 현재 서버에 접속중이 아님.", receiverKey);
            }

        } catch (JacksonException e) {
            log.error("Redis 메시지 처리 중 오류 발생", e);
        }
    }
}

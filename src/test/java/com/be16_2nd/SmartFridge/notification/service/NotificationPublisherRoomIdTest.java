package com.be16_2nd.SmartFridge.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * SSE 알림에 채팅방 id가 실리는지 확인하는 단위 테스트.
 * 프론트는 이 roomId로 어느 방의 미읽음 수를 올릴지 정한다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationPublisherRoomIdTest {

    @Mock RedisTemplate<String, String> redisTemplate;

    final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void 채팅_알림에는_방_id를_싣는다() {
        new NotificationPublisher(redisTemplate, objectMapper)
                .publish(7L, "receiver@test.com", "새 채팅", "GROUP_CHAT", 42L);

        JsonNode payload = publishedPayload();
        assertThat(payload.get("type").asString()).isEqualTo("GROUP_CHAT");
        assertThat(payload.get("fridgeId").asLong()).isEqualTo(7L);
        assertThat(payload.get("roomId").asLong()).isEqualTo(42L);
    }

    @Test
    void 채팅이_아닌_알림은_방_id가_없다() {
        new NotificationPublisher(redisTemplate, objectMapper)
                .publish(7L, "receiver@test.com", "새 문의", "NEW_INQUIRY");

        assertThat(publishedPayload().get("roomId").isNull()).isTrue();
    }

    private JsonNode publishedPayload() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("sse-channel"), captor.capture());
        return objectMapper.readTree(captor.getValue());
    }
}

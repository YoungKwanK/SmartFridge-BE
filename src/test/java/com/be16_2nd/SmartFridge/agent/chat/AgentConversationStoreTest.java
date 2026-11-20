package com.be16_2nd.SmartFridge.agent.chat;

import com.be16_2nd.SmartFridge.agent.chat.AgentConversationStore.Turn;
import com.be16_2nd.SmartFridge.agent.domain.AgentActionStatus;
import com.be16_2nd.SmartFridge.agent.domain.AgentActionType;
import com.be16_2nd.SmartFridge.agent.dto.AgentActionResDto;
import com.be16_2nd.SmartFridge.food.dto.FoodResDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 대화 기록 저장 단위 테스트. Redis는 가짜로 두고, 저장한 JSON을 그대로 다시 읽어서 확인한다
 * (확인 카드 안의 날짜까지 새로고침 뒤에 그대로 돌아오는지).
 */
class AgentConversationStoreTest {

    StringRedisTemplate redisTemplate;
    ValueOperations<String, String> values;
    AgentConversationStore store;

    final Long fridgeId = 1L;
    final UUID memberId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        store = new AgentConversationStore(redisTemplate, JsonMapper.builder().build());
    }

    @Test
    void 확인_카드까지_저장했다가_그대로_다시_읽는다() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 11, 18, 17, 10);
        AgentActionResDto card = AgentActionResDto.builder()
                .id(UUID.randomUUID()).type(AgentActionType.FINISH_CONSUMED).status(AgentActionStatus.PENDING)
                .foods(List.of(FoodResDto.builder().id(1L).name("우유").quantity(1)
                        .storageStartDate(LocalDate.of(2026, 11, 15))
                        .expirationDateTime(LocalDateTime.of(2026, 11, 19, 23, 59)).build()))
                .expiresAt(expiresAt)
                .build();

        store.append(fridgeId, memberId, List.of(), "우유 마셨어", "우유를 다 먹은 것으로 기록할까요?", List.of(card));

        String saved = savedJson();
        when(values.get(anyString())).thenReturn(saved);
        List<Turn> turns = store.load(fridgeId, memberId);

        assertThat(turns).hasSize(2);
        assertThat(turns.get(0)).isEqualTo(Turn.user("우유 마셨어"));
        Turn answer = turns.get(1);
        assertThat(answer.text()).isEqualTo("우유를 다 먹은 것으로 기록할까요?");
        assertThat(answer.actions()).singleElement().satisfies(restored -> {
            assertThat(restored.getId()).isEqualTo(card.getId());
            assertThat(restored.getExpiresAt()).isEqualTo(expiresAt);
            assertThat(restored.getFoods().get(0).getExpirationDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 19, 23, 59));
        });
    }

    @Test
    void 메시지는_최근_20개만_남기고_항상_질문부터_시작한다() {
        List<Turn> history = new ArrayList<>();
        IntStream.range(0, 10).forEach(i -> {
            history.add(Turn.user("질문" + i));
            history.add(Turn.assistant("답" + i, List.of()));
        });

        store.append(fridgeId, memberId, history, "새 질문", "새 답", List.of());

        String saved = savedJson();
        when(values.get(anyString())).thenReturn(saved);
        List<Turn> turns = store.load(fridgeId, memberId);
        assertThat(turns).hasSize(AgentConversationStore.MAX_MESSAGES);
        assertThat(turns.get(0)).isEqualTo(Turn.user("질문1"));
        assertThat(turns.get(turns.size() - 1).text()).isEqualTo("새 답");
    }

    @Test
    void 예전_형식이나_깨진_기록은_새_대화로_시작한다() {
        // 카드 칸이 없던 예전 형식은 카드 없이 읽고, 깨진 JSON은 버린다
        when(values.get(anyString())).thenReturn("[{\"role\":\"user\",\"text\":\"안녕\"}]");
        assertThat(store.load(fridgeId, memberId)).containsExactly(Turn.user("안녕"));

        when(values.get(anyString())).thenReturn("{broken");
        assertThat(store.load(fridgeId, memberId)).isEmpty();
    }

    private String savedJson() {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("agent:conversation:" + fridgeId + ":" + memberId), json.capture(), eq(Duration.ofMinutes(30)));
        return json.getValue();
    }
}

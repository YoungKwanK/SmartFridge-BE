package com.be16_2nd.SmartFridge.agent.chat;

import com.anthropic.core.ObjectMappers;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 에이전트 대화 기록과 하루 사용량을 Redis에 둔다.
 *
 * 대화: 냉장고·회원별로 최근 {@value #MAX_MESSAGES}개 메시지(사용자 질문과 최종 답변 글)만 30분 동안 보관한다.
 *       도구 호출 과정은 저장하지 않는다. 다음 질문에서 필요하면 Claude가 다시 조회한다 (항상 최신 데이터 기준).
 * 사용량: 회원별로 하루 {@value #DAILY_LIMIT}번까지. 날짜가 바뀌면 새로 센다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentConversationStore {

    public static final int MAX_MESSAGES = 20;
    public static final int DAILY_LIMIT = 30;
    private static final Duration CONVERSATION_TTL = Duration.ofMinutes(30);
    private static final Duration USAGE_TTL = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;

    /** 대화 한 줄. role은 "user" 또는 "assistant" */
    public record Turn(String role, String text) {}

    public List<Turn> load(Long fridgeId, UUID memberId) {
        String json = redisTemplate.opsForValue().get(conversationKey(fridgeId, memberId));
        if (json == null) {
            return List.of();
        }
        try {
            return ObjectMappers.jsonMapper().readValue(json, new TypeReference<List<Turn>>() {});
        } catch (Exception e) {
            // 형식이 깨진 기록은 버리고 새 대화로 시작한다
            log.warn("에이전트 대화 기록을 읽지 못해 새로 시작 key={}", conversationKey(fridgeId, memberId));
            return List.of();
        }
    }

    /** 이번 질문과 답을 붙여 저장한다. 오래된 것부터 버리되 항상 사용자 질문으로 시작하게 둘씩 버린다 */
    public void append(Long fridgeId, UUID memberId, List<Turn> history, String userText, String assistantText) {
        List<Turn> turns = new ArrayList<>(history);
        turns.add(new Turn("user", userText));
        turns.add(new Turn("assistant", assistantText));
        while (turns.size() > MAX_MESSAGES) {
            turns.remove(0);
            turns.remove(0);
        }
        try {
            redisTemplate.opsForValue().set(conversationKey(fridgeId, memberId),
                    ObjectMappers.jsonMapper().writeValueAsString(turns), CONVERSATION_TTL);
        } catch (Exception e) {
            // 기록 저장 실패는 답변을 막지 않는다 (다음 질문이 새 대화로 시작될 뿐)
            log.warn("에이전트 대화 기록 저장 실패: {}", e.getMessage());
        }
    }

    public void clear(Long fridgeId, UUID memberId) {
        redisTemplate.delete(conversationKey(fridgeId, memberId));
    }

    /** 오늘 사용 횟수를 하나 올리고, 올린 뒤의 횟수를 돌려준다 */
    public long increaseUsage(UUID memberId) {
        String key = usageKey(memberId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, USAGE_TTL);
        }
        return count == null ? 0L : count;
    }

    private static String conversationKey(Long fridgeId, UUID memberId) {
        return "agent:conversation:" + fridgeId + ":" + memberId;
    }

    private static String usageKey(UUID memberId) {
        return "agent:usage:" + memberId + ":" + LocalDate.now();
    }
}

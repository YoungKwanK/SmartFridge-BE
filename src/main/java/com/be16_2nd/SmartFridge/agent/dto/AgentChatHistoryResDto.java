package com.be16_2nd.SmartFridge.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 지금까지의 대화 (최근 30분, 최대 20개 메시지). 화면을 열거나 새로고침할 때 이걸로 대화를 다시 그린다.
 * 확인 카드의 status는 지금 상태다 (PENDING이면 아직 누를 수 있다).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentChatHistoryResDto {
    private List<Message> messages;
    private long remainingToday;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        // "user" 또는 "assistant"
        private String role;
        private String text;
        private List<AgentActionResDto> actions;
    }
}

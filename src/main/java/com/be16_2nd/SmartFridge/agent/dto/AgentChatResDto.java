package com.be16_2nd.SmartFridge.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 에이전트 답변. actions가 비어 있으면 말풍선만, 있으면 말풍선 아래에 확인 카드를 붙인다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentChatResDto {
    private String message;
    private List<AgentActionResDto> actions;
    // 오늘 남은 질문 횟수
    private long remainingToday;
}

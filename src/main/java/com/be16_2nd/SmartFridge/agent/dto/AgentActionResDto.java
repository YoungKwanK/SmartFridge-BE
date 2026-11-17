package com.be16_2nd.SmartFridge.agent.dto;

import com.be16_2nd.SmartFridge.agent.domain.AgentAction;
import com.be16_2nd.SmartFridge.agent.domain.AgentActionStatus;
import com.be16_2nd.SmartFridge.agent.domain.AgentActionType;
import com.be16_2nd.SmartFridge.food.dto.FoodResDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 에이전트가 만든 제안. 화면은 이것으로 확인 카드를 그린다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentActionResDto {
    private UUID id;
    private AgentActionType type;
    private AgentActionStatus status;
    private List<FoodResDto> foods;
    private LocalDateTime expiresAt;

    public static AgentActionResDto of(AgentAction action, List<FoodResDto> foods) {
        return AgentActionResDto.builder()
                .id(action.getId())
                .type(action.getType())
                .status(action.getStatus())
                .foods(foods)
                .expiresAt(action.getExpiresAt())
                .build();
    }
}

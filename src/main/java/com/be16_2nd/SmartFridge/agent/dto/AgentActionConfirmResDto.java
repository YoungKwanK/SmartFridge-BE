package com.be16_2nd.SmartFridge.agent.dto;

import com.be16_2nd.SmartFridge.agent.domain.AgentActionStatus;
import com.be16_2nd.SmartFridge.agent.domain.AgentActionType;
import com.be16_2nd.SmartFridge.food.dto.FoodBulkResDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 확인 버튼을 눌러 실행한 결과. 처리한 식품 수와 이름을 함께 돌려준다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentActionConfirmResDto {
    private UUID id;
    private AgentActionType type;
    private AgentActionStatus status;
    private FoodBulkResDto result;
}

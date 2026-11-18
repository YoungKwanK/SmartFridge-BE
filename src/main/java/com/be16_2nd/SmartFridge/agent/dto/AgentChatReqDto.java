package com.be16_2nd.SmartFridge.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentChatReqDto {
    @NotBlank(message = "메시지를 입력해 주세요.")
    @Size(max = 500, message = "메시지는 500자까지 보낼 수 있어요.")
    private String message;
}

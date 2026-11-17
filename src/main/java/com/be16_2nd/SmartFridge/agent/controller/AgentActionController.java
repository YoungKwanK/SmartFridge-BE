package com.be16_2nd.SmartFridge.agent.controller;

import com.be16_2nd.SmartFridge.agent.dto.AgentActionConfirmResDto;
import com.be16_2nd.SmartFridge.agent.service.AgentActionService;
import com.be16_2nd.SmartFridge.common.dto.CommonDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * AI 에이전트 제안의 확인 카드 버튼. 에이전트(LLM)는 이 API를 부를 수 없고, 사용자만 누를 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/fridge/{fridgeId}/agent/actions")
public class AgentActionController {

    private final AgentActionService agentActionService;

    @PostMapping("/{actionId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable("fridgeId") Long fridgeId,
                                     @PathVariable("actionId") UUID actionId) {
        AgentActionConfirmResDto result = agentActionService.confirm(fridgeId, actionId);
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(result)
                        .status_code(HttpStatus.OK.value())
                        .status_message("제안 실행 완료")
                        .build(),
                HttpStatus.OK);
    }

    @PostMapping("/{actionId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable("fridgeId") Long fridgeId,
                                    @PathVariable("actionId") UUID actionId) {
        agentActionService.cancel(fridgeId, actionId);
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result("CANCELLED")
                        .status_code(HttpStatus.OK.value())
                        .status_message("제안 취소 완료")
                        .build(),
                HttpStatus.OK);
    }
}

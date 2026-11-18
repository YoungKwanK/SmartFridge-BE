package com.be16_2nd.SmartFridge.agent.controller;

import com.be16_2nd.SmartFridge.agent.dto.AgentChatReqDto;
import com.be16_2nd.SmartFridge.agent.dto.AgentChatResDto;
import com.be16_2nd.SmartFridge.agent.service.AgentChatService;
import com.be16_2nd.SmartFridge.common.dto.CommonDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI 도우미 채팅. 답변과 함께, 정리 제안이 있으면 확인 카드(actions)를 내려준다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/fridge/{fridgeId}/agent/chat")
public class AgentChatController {

    private final AgentChatService agentChatService;

    // 예: { "message": "유통기한 지난 거 뭐 있어?" }
    @PostMapping
    public ResponseEntity<?> chat(@PathVariable("fridgeId") Long fridgeId,
                                  @RequestBody @Valid AgentChatReqDto agentChatReqDto) {
        AgentChatResDto result = agentChatService.chat(fridgeId, agentChatReqDto.getMessage());
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(result)
                        .status_code(HttpStatus.OK.value())
                        .status_message("AI 답변 완료")
                        .build(),
                HttpStatus.OK);
    }

    // 새 대화 시작 (이전 대화 기록 삭제)
    @DeleteMapping
    public ResponseEntity<?> reset(@PathVariable("fridgeId") Long fridgeId) {
        agentChatService.reset(fridgeId);
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result("RESET")
                        .status_code(HttpStatus.OK.value())
                        .status_message("대화 기록 삭제 완료")
                        .build(),
                HttpStatus.OK);
    }
}

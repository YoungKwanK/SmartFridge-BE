package com.be16_2nd.SmartFridge.agent.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.be16_2nd.SmartFridge.agent.chat.AgentConversationStore;
import com.be16_2nd.SmartFridge.agent.chat.AgentConversationStore.Turn;
import com.be16_2nd.SmartFridge.agent.chat.AgentPrompts;
import com.be16_2nd.SmartFridge.agent.chat.AgentToolExecutor;
import com.be16_2nd.SmartFridge.agent.chat.AgentToolExecutor.ToolOutcome;
import com.be16_2nd.SmartFridge.agent.chat.AgentTools;
import com.be16_2nd.SmartFridge.agent.dto.AgentActionResDto;
import com.be16_2nd.SmartFridge.agent.dto.AgentChatResDto;
import com.be16_2nd.SmartFridge.agent.exception.AgentChatException;
import com.be16_2nd.SmartFridge.common.ai.AiException;
import com.be16_2nd.SmartFridge.common.ai.AiProperties;
import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.dto.FoodResDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * AI 에이전트 채팅. Claude에 질문과 도구를 보내고, 도구 요청이 오면 실행해 결과를 돌려주는 일을
 * 답이 나올 때까지 반복한다 (최대 {@value #MAX_ROUNDS}번).
 *
 * <ul>
 *   <li>Claude 클라이언트·설정·오류 형식은 공용 AI 모듈(AiConfig, AiProperties, AiException)을 그대로 쓴다</li>
 *   <li>식품을 바꾸는 도구는 없다. 정리는 제안(확인 카드)만 만들고, 실행은 사용자가 확인 버튼을 눌러야 한다</li>
 *   <li>Claude 응답을 기다리는 동안 DB 커넥션을 잡지 않도록 트랜잭션을 걸지 않는다 (도구가 각자 짧게 건다)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    static final int MAX_ROUNDS = 6;
    static final String GAVE_UP_MESSAGE = "요청을 끝까지 처리하지 못했어요. 조금 더 구체적으로 다시 말씀해 주세요.";

    private final AnthropicClient anthropicClient;
    private final AiProperties aiProperties;
    private final AgentToolExecutor toolExecutor;
    private final AgentConversationStore conversationStore;
    private final FridgeAccessValidator fridgeAccessValidator;

    public AgentChatResDto chat(Long fridgeId, String userMessage) {
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);
        UUID memberId = context.member().getId();

        long used = conversationStore.increaseUsage(memberId);
        if (used > AgentConversationStore.DAILY_LIMIT) {
            throw AgentChatException.dailyLimitExceeded(AgentConversationStore.DAILY_LIMIT);
        }

        List<Turn> history = conversationStore.load(fridgeId, memberId);
        MessageCreateParams params = initialParams(context, history, userMessage);

        List<AgentActionResDto> actions = new ArrayList<>();
        String answer = null;
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            MessageCreateParams request = params;
            Message response = call(() -> anthropicClient.messages().create(request));
            log.debug("에이전트 round={} stop={} input={} output={}", round, response.stopReason().orElse(null),
                    response.usage().inputTokens(), response.usage().outputTokens());
            validateStopReason(response);

            List<ToolUseBlock> toolUses = response.content().stream()
                    .flatMap(block -> block.toolUse().stream())
                    .toList();
            if (toolUses.isEmpty()) {
                answer = text(response);
                break;
            }

            // 한 응답에 도구 요청이 여러 개면 결과도 한 메시지에 모아 돌려준다
            List<ContentBlockParam> results = new ArrayList<>();
            for (ToolUseBlock toolUse : toolUses) {
                ToolOutcome outcome = toolExecutor.execute(fridgeId, context.type(), toolUse.name(), toolUse._input());
                if (outcome.action() != null) {
                    actions.add(outcome.action());
                }
                results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(toolUse.id())
                        .content(outcome.content())
                        .isError(outcome.error())
                        .build()));
            }
            params = params.toBuilder()
                    .addMessage(response)
                    .addUserMessageOfContentBlockParams(results)
                    .build();
        }

        String message = finalMessage(answer, actions);
        conversationStore.append(fridgeId, memberId, history, userMessage, withActionNote(message, actions));
        return AgentChatResDto.builder()
                .message(message)
                .actions(actions)
                .remainingToday(Math.max(0, AgentConversationStore.DAILY_LIMIT - used))
                .build();
    }

    /** 대화 기록을 지우고 새로 시작한다 */
    public void reset(Long fridgeId) {
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);
        conversationStore.clear(fridgeId, context.member().getId());
    }

    private MessageCreateParams initialParams(FridgeAccessValidator.FridgeContext context, List<Turn> history, String userMessage) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(aiProperties.model())
                .maxTokens(aiProperties.maxTokens())
                // 바뀌지 않는 규칙을 앞에, 요청마다 바뀌는 정보(날짜, 권한)를 뒤에 둔다
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder().text(AgentPrompts.RULES).build(),
                        TextBlockParam.builder().text(AgentPrompts.context(LocalDateTime.now(), context.type())).build()));
        AgentTools.all().forEach(builder::addTool);

        for (Turn turn : history) {
            if ("assistant".equals(turn.role())) {
                builder.addAssistantMessage(turn.text());
            } else {
                builder.addUserMessage(turn.text());
            }
        }
        builder.addUserMessage(userMessage);
        return builder.build();
    }

    private static String text(Message response) {
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining())
                .strip();
    }

    private static String finalMessage(String answer, List<AgentActionResDto> actions) {
        if (answer != null && !answer.isBlank()) {
            return answer;
        }
        if (!actions.isEmpty()) {
            return "아래 확인 카드를 보고 맞으면 버튼을 눌러 주세요.";
        }
        return GAVE_UP_MESSAGE;
    }

    // 다음 질문에서 Claude가 "아까 제안"을 알 수 있게 대화 기록에만 제안 내용을 덧붙인다
    private static String withActionNote(String message, List<AgentActionResDto> actions) {
        if (actions.isEmpty()) {
            return message;
        }
        String note = actions.stream()
                .map(action -> action.getType() + " " + action.getFoods().stream()
                        .map(FoodResDto::getName).collect(Collectors.joining(", ")))
                .collect(Collectors.joining(" / "));
        return message + "\n[확인 카드를 띄움: " + note + " - 사용자 확인 대기]";
    }

    private void validateStopReason(Message response) {
        StopReason reason = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(reason)) {
            throw new AiException(HttpStatus.BAD_GATEWAY, "AI가 이 요청에는 답할 수 없다고 했어요.");
        }
        if (StopReason.MAX_TOKENS.equals(reason)) {
            log.error("에이전트 응답이 max_tokens 에서 잘림");
            throw new AiException(HttpStatus.BAD_GATEWAY, "AI 응답이 너무 길어 중간에 끊겼습니다.");
        }
    }

    // SDK 예외를 공용 AI 예외로 바꾼다 (AiClient와 같은 규칙). 429·5xx·네트워크 오류는 SDK가 이미 재시도한 뒤다
    private static <R> R call(Supplier<R> request) {
        try {
            return request.get();
        } catch (RateLimitException e) {
            log.warn("에이전트 AI 요청 한도 초과: {}", e.getMessage());
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI 요청이 많아 잠시 후 다시 시도해 주세요.", e);
        } catch (AnthropicServiceException e) {
            log.error("에이전트 AI API 오류 status={} message={}", e.statusCode(), e.getMessage());
            HttpStatus status = e.statusCode() >= 500 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            throw new AiException(status, "AI 응답을 받지 못했어요. 잠시 후 다시 시도해 주세요.", e);
        } catch (AnthropicIoException e) {
            log.error("에이전트 AI API 연결 실패: {}", e.getMessage());
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.", e);
        } catch (AnthropicInvalidDataException e) {
            log.error("에이전트 AI 응답 형식 오류: {}", e.getMessage());
            throw new AiException(HttpStatus.BAD_GATEWAY, "AI 응답 형식이 올바르지 않습니다.", e);
        }
    }
}

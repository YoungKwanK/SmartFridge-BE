package com.be16_2nd.SmartFridge.common.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 여러 기능(레시피 추천 등)에서 같이 쓰는 Claude 호출 창구
 *
 * <pre>
 * // 1) 글로 받기
 * String text = aiClient.generate(AiPrompt.of(SYSTEM_PROMPT, userInput));
 *
 * // 2) DTO(record)로 받기 - 클래스 구조대로 JSON 스키마를 만들어 그 형식으로만 답하게 한다
 * RecipeRecommendation result = aiClient.generate(AiPrompt.of(SYSTEM_PROMPT, userInput), RecipeRecommendation.class);
 * </pre>
 *
 * 실패하면 {@link AiException} 을 던지고, CommonExceptionHandler 가 상태 코드로 바꿔 응답한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final AnthropicClient anthropicClient;
    private final AiProperties properties;

    /** 응답을 문자열로 받는다 */
    public String generate(AiPrompt prompt) {
        MessageCreateParams.Builder builder = baseBuilder(prompt);
        if (prompt.effort() != null) {
            builder.outputConfig(OutputConfig.builder().effort(prompt.effort().toSdk()).build());
        }

        Message message = call(() -> anthropicClient.messages().create(builder.build()));
        validateStopReason(message.stopReason());
        logUsage(message.usage());

        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        if (text.isBlank()) {
            throw new AiException(HttpStatus.BAD_GATEWAY, "AI 응답이 비어 있습니다.");
        }
        return text;
    }

    /** 응답을 지정한 타입(record/DTO)으로 받는다 */
    public <T> T generate(AiPrompt prompt, Class<T> responseType) {
        StructuredOutputConfig.Builder<T> outputConfig = StructuredOutputConfig.<T>builder().format(responseType);
        if (prompt.effort() != null) {
            outputConfig.effort(prompt.effort().toSdk());
        }
        StructuredMessageCreateParams<T> params = baseBuilder(prompt)
                .outputConfig(outputConfig.build())
                .build();

        StructuredMessage<T> message = call(() -> anthropicClient.messages().create(params));
        validateStopReason(message.stopReason());
        logUsage(message.usage());

        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> call(textBlock::text))   // JSON → T 변환 실패도 AiException 으로
                .findFirst()
                .orElseThrow(() -> new AiException(HttpStatus.BAD_GATEWAY, "AI 응답이 비어 있습니다."));
    }

    private MessageCreateParams.Builder baseBuilder(AiPrompt prompt) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(prompt.model() != null ? prompt.model() : properties.model())
                .maxTokens(prompt.maxTokens() != null ? prompt.maxTokens() : properties.maxTokens())
                .addUserMessage(prompt.user());
        if (prompt.system() != null && !prompt.system().isBlank()) {
            builder.system(prompt.system());
        }
        return builder;
    }

    // SDK 예외를 서비스 예외로 바꾼다. 429·5xx·네트워크 오류는 SDK가 이미 재시도한 뒤의 실패다
    private <R> R call(Supplier<R> request) {
        try {
            return request.get();
        } catch (RateLimitException e) {
            log.warn("AI 요청 한도 초과: {}", e.getMessage());
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI 요청이 많아 잠시 후 다시 시도해 주세요.", e);
        } catch (AnthropicServiceException e) {
            log.error("AI API 오류 status={} message={}", e.statusCode(), e.getMessage());
            HttpStatus status = e.statusCode() >= 500 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            throw new AiException(status, "AI 응답을 받지 못했어요. 잠시 후 다시 시도해 주세요.", e);
        } catch (AnthropicIoException e) {
            log.error("AI API 연결 실패: {}", e.getMessage());
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.", e);
        } catch (AnthropicInvalidDataException e) {
            log.error("AI 응답 형식 오류: {}", e.getMessage());
            throw new AiException(HttpStatus.BAD_GATEWAY, "AI 응답 형식이 올바르지 않습니다.", e);
        }
    }

    private void validateStopReason(Optional<StopReason> stopReason) {
        if (stopReason.isEmpty()) {
            return;
        }
        StopReason reason = stopReason.get();
        if (StopReason.REFUSAL.equals(reason)) {
            throw new AiException(HttpStatus.BAD_GATEWAY, "AI가 이 요청에는 답할 수 없다고 했어요.");
        }
        if (StopReason.MAX_TOKENS.equals(reason)) {
            // 응답이 중간에 잘림 → max-tokens 를 늘리거나 요청을 줄여야 한다
            log.error("AI 응답이 max_tokens 에서 잘림");
            throw new AiException(HttpStatus.BAD_GATEWAY, "AI 응답이 너무 길어 중간에 끊겼습니다.");
        }
    }

    private void logUsage(Usage usage) {
        log.debug("AI 토큰 사용량 input={} output={} cacheRead={}",
                usage.inputTokens(), usage.outputTokens(), usage.cacheReadInputTokens().orElse(0L));
    }
}

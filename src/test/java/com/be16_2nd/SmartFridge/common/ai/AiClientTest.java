package com.be16_2nd.SmartFridge.common.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiClientTest {

    private AnthropicClient anthropicClient;
    private AiClient aiClient;

    @BeforeEach
    void setUp() {
        anthropicClient = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
        AiProperties properties = new AiProperties("test-key", "claude-haiku-5-5", 16000L, Duration.ofSeconds(60), 2);
        aiClient = new AiClient(anthropicClient, properties);
    }

    @Test
    void 텍스트_응답을_이어붙여_돌려준다() throws Exception {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenReturn(message("end_turn", "계란말이를 ", "추천해요"));

        String result = aiClient.generate(AiPrompt.of("너는 셰프야", "계란, 양파"));

        assertThat(result).isEqualTo("계란말이를 추천해요");
    }

    @Test
    void 기능별로_모델과_effort를_바꿔_요청할_수_있다() throws Exception {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenReturn(message("end_turn", "ok"));
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        aiClient.generate(AiPrompt.builder()
                .system("규칙")
                .user("입력")
                .model("claude-sonnet-5-5")
                .maxTokens(1000L)
                .effort(AiEffort.LOW)
                .build());

        verify(anthropicClient.messages()).create(captor.capture());
        MessageCreateParams params = captor.getValue();
        assertThat(params.model().asString()).isEqualTo("claude-sonnet-5-5");
        assertThat(params.maxTokens()).isEqualTo(1000L);
        assertThat(params.outputConfig().orElseThrow().effort().orElseThrow().asString()).isEqualTo("low");
    }

    @Test
    void 기본값은_설정파일의_모델과_max_tokens를_쓴다() throws Exception {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenReturn(message("end_turn", "ok"));
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);

        aiClient.generate(AiPrompt.of(null, "입력"));

        verify(anthropicClient.messages()).create(captor.capture());
        assertThat(captor.getValue().model().asString()).isEqualTo("claude-haiku-5-5");
        assertThat(captor.getValue().maxTokens()).isEqualTo(16000L);
        assertThat(captor.getValue().system()).isEmpty();
    }

    @Test
    void 거절되면_502() throws Exception {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenReturn(message("refusal"));

        assertThatThrownBy(() -> aiClient.generate(AiPrompt.of("규칙", "입력")))
                .isInstanceOf(AiException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void 응답이_max_tokens에서_잘리면_502() throws Exception {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenReturn(message("max_tokens", "잘린 응답"));

        assertThatThrownBy(() -> aiClient.generate(AiPrompt.of("규칙", "입력")))
                .isInstanceOf(AiException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void 요청_한도를_넘으면_503() {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenThrow(mock(RateLimitException.class));

        assertThatThrownBy(() -> aiClient.generate(AiPrompt.of("규칙", "입력")))
                .isInstanceOf(AiException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void 연결에_실패하면_503() {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenThrow(new AnthropicIoException("connection refused"));

        assertThatThrownBy(() -> aiClient.generate(AiPrompt.of("규칙", "입력")))
                .isInstanceOf(AiException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void 요청_내용이_비어_있으면_보내지_않는다() {
        assertThatThrownBy(() -> AiPrompt.of("규칙", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 실제 API 응답과 같은 모양의 JSON 으로 Message 를 만든다
    private Message message(String stopReason, String... texts) throws Exception {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < texts.length; i++) {
            if (i > 0) content.append(',');
            content.append("{\"type\":\"text\",\"text\":\"").append(texts[i]).append("\"}");
        }
        String json = """
                {"id":"msg_test","type":"message","role":"assistant","model":"claude-haiku-5-5",
                 "content":[%s],"stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """.formatted(content, stopReason);
        return ObjectMappers.jsonMapper().readValue(json, Message.class);
    }
}

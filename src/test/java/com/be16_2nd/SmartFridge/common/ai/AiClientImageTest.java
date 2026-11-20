package com.be16_2nd.SmartFridge.common.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiClientImageTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};

    private AnthropicClient anthropicClient;
    private AiClient aiClient;

    @BeforeEach
    void setUp() throws Exception {
        anthropicClient = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
        AiProperties properties = new AiProperties("test-key", "claude-haiku-5-5", 16000L, Duration.ofSeconds(60), 2);
        aiClient = new AiClient(anthropicClient, properties);
        when(anthropicClient.messages().create(any(MessageCreateParams.class))).thenReturn(message());
    }

    @Test
    void 이미지는_base64로_바꿔_글보다_먼저_보낸다() {
        aiClient.generate(AiPrompt.builder()
                .system("규칙")
                .user("이 사진을 읽어 줘")
                .images(List.of(new AiImage(JPEG, "image/jpeg")))
                .build());

        MessageParam userMessage = sentUserMessage();
        assertThat(userMessage.content().isContentBlockParams()).isTrue();
        List<ContentBlockParam> blocks = userMessage.content().asContentBlockParams();
        assertThat(blocks).hasSize(2);

        Base64ImageSource source = blocks.get(0).asImage().source().asBase64();
        assertThat(source.mediaType()).isEqualTo(Base64ImageSource.MediaType.IMAGE_JPEG);
        assertThat(Base64.getDecoder().decode(source.data())).isEqualTo(JPEG);
        assertThat(blocks.get(1).asText().text()).isEqualTo("이 사진을 읽어 줘");
    }

    @Test
    void 이미지가_없으면_지금처럼_글만_보낸다() {
        aiClient.generate(AiPrompt.of("규칙", "계란, 양파"));

        MessageParam userMessage = sentUserMessage();
        assertThat(userMessage.content().isString()).isTrue();
        assertThat(userMessage.content().asString()).isEqualTo("계란, 양파");
    }

    @Test
    void 비어_있거나_이미지가_아닌_형식은_만들_수_없다() {
        assertThatThrownBy(() -> new AiImage(new byte[0], "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiImage(JPEG, "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MessageParam sentUserMessage() {
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(anthropicClient.messages()).create(captor.capture());
        return captor.getValue().messages().get(0);
    }

    private Message message() throws Exception {
        String json = """
                {"id":"msg_test","type":"message","role":"assistant","model":"claude-haiku-5-5",
                 "content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """;
        return ObjectMappers.jsonMapper().readValue(json, Message.class);
    }
}

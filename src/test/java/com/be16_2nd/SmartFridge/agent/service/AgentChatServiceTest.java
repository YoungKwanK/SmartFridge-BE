package com.be16_2nd.SmartFridge.agent.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.be16_2nd.SmartFridge.agent.chat.AgentConversationStore;
import com.be16_2nd.SmartFridge.agent.chat.AgentConversationStore.Turn;
import com.be16_2nd.SmartFridge.agent.chat.AgentToolExecutor;
import com.be16_2nd.SmartFridge.agent.chat.AgentToolExecutor.ToolOutcome;
import com.be16_2nd.SmartFridge.agent.domain.AgentActionStatus;
import com.be16_2nd.SmartFridge.agent.domain.AgentActionType;
import com.be16_2nd.SmartFridge.agent.dto.AgentActionResDto;
import com.be16_2nd.SmartFridge.agent.dto.AgentChatHistoryResDto;
import com.be16_2nd.SmartFridge.agent.dto.AgentChatResDto;
import com.be16_2nd.SmartFridge.agent.exception.AgentChatException;
import com.be16_2nd.SmartFridge.common.ai.AiException;
import com.be16_2nd.SmartFridge.common.ai.AiProperties;
import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.dto.FoodResDto;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 에이전트 루프 단위 테스트. Claude 응답은 실제 API와 같은 모양의 JSON으로 가짜를 만들어 비용 없이 확인한다.
 */
class AgentChatServiceTest {

    AnthropicClient anthropicClient;
    AgentToolExecutor toolExecutor;
    AgentConversationStore conversationStore;
    FridgeAccessValidator fridgeAccessValidator;
    AgentActionService agentActionService;
    AgentChatService agentChatService;

    final Long fridgeId = 1L;
    final Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
    final Member me = Member.builder().id(UUID.randomUUID()).name("홍길동").build();

    @BeforeEach
    void setUp() {
        anthropicClient = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
        toolExecutor = mock(AgentToolExecutor.class);
        conversationStore = mock(AgentConversationStore.class);
        fridgeAccessValidator = mock(FridgeAccessValidator.class);
        agentActionService = mock(AgentActionService.class);
        AiProperties properties = new AiProperties("test-key", "claude-haiku-5-5", 16000L, Duration.ofSeconds(60), 2);
        agentChatService = new AgentChatService(anthropicClient, properties, toolExecutor, conversationStore,
                fridgeAccessValidator, agentActionService);

        loginAs(Type.COMMON);
        when(conversationStore.increaseUsage(me.getId())).thenReturn(1L);
        when(conversationStore.load(fridgeId, me.getId())).thenReturn(List.of());
    }

    @Test
    void 도구_없이_바로_답하면_그_답을_돌려주고_대화를_저장한다() throws Exception {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenReturn(message("end_turn", text("안녕하세요. 냉장고 식품을 찾아 드릴게요.")));

        AgentChatResDto res = agentChatService.chat(fridgeId, "안녕");

        assertThat(res.getMessage()).isEqualTo("안녕하세요. 냉장고 식품을 찾아 드릴게요.");
        assertThat(res.getActions()).isEmpty();
        assertThat(res.getRemainingToday()).isEqualTo(29);
        verify(conversationStore).append(fridgeId, me.getId(), List.of(), "안녕", "안녕하세요. 냉장고 식품을 찾아 드릴게요.", List.of());
        verifyNoInteractions(toolExecutor);
    }

    @Test
    void 도구를_요청하면_실행해서_결과를_돌려주고_최종_답을_받는다() throws Exception {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenReturn(
                        message("tool_use", toolUse("toolu_1", "find_foods", "{\"keyword\":\"우유\"}")),
                        message("end_turn", text("우유는 내일까지예요.")));
        when(toolExecutor.execute(eq(fridgeId), eq(Type.COMMON), eq("find_foods"), any()))
                .thenReturn(new ToolOutcome("{\"total\":1,\"foods\":[{\"id\":12,\"name\":\"우유\",\"daysLeft\":1}]}", false, null));

        AgentChatResDto res = agentChatService.chat(fridgeId, "우유 유통기한 언제까지야?");

        assertThat(res.getMessage()).isEqualTo("우유는 내일까지예요.");
        List<MessageCreateParams> requests = sentRequests(2);
        String second = body(requests.get(1));
        // 두 번째 요청에 Claude의 도구 요청과 그 결과가 이어 붙는다
        assertThat(second).contains("\"type\":\"tool_use\"").contains("\"tool_use_id\":\"toolu_1\"")
                .contains("\"type\":\"tool_result\"").contains("daysLeft");
    }

    @Test
    void 제안_도구가_만든_확인_카드를_응답에_담고_대화_기록에_카드를_함께_저장한다() throws Exception {
        AgentActionResDto action = tofuCard(UUID.randomUUID());
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenReturn(
                        message("tool_use", toolUse("toolu_1", "propose_finish", "{\"foodIds\":[12],\"result\":\"폐기\"}")),
                        message("end_turn", text("두부를 버린 것으로 정리할까요? 확인 카드를 눌러 주세요.")));
        when(toolExecutor.execute(eq(fridgeId), eq(Type.COMMON), eq("propose_finish"), any()))
                .thenReturn(new ToolOutcome("{\"status\":\"확인 대기\"}", false, action));

        AgentChatResDto res = agentChatService.chat(fridgeId, "두부 버렸어");

        assertThat(res.getActions()).containsExactly(action);
        // 화면에 보일 답은 그대로, 카드는 따로 저장한다 (AI용 메모가 답 문장에 섞이지 않게)
        verify(conversationStore).append(fridgeId, me.getId(), List.of(), "두부 버렸어",
                "두부를 버린 것으로 정리할까요? 확인 카드를 눌러 주세요.", List.of(action));
    }

    @Test
    void 지난_답변의_확인_카드와_지금_상태를_Claude에게_알려준다() throws Exception {
        UUID cardId = UUID.randomUUID();
        when(conversationStore.load(fridgeId, me.getId())).thenReturn(List.of(
                Turn.user("두부 버렸어"),
                Turn.assistant("두부를 버린 것으로 정리할까요?", List.of(tofuCard(cardId)))));
        when(agentActionService.currentStatuses(List.of(cardId)))
                .thenReturn(Map.of(cardId, AgentActionStatus.CONFIRMED));
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenReturn(message("end_turn", text("네, 두부는 처리됐어요.")));

        agentChatService.chat(fridgeId, "처리됐어?");

        assertThat(body(sentRequests(1).get(0)))
                .contains("두부를 버린 것으로 정리할까요?")
                .contains("이 답변에서 띄운 확인 카드: 버림 두부 (사용자가 확인해 처리됨)");
    }

    @Test
    void 대화_기록은_화면용_문장과_지금_카드_상태로_돌려준다() {
        UUID cardId = UUID.randomUUID();
        when(conversationStore.load(fridgeId, me.getId())).thenReturn(List.of(
                Turn.user("두부 버렸어"),
                Turn.assistant("두부를 버린 것으로 정리할까요?", List.of(tofuCard(cardId)))));
        when(agentActionService.currentStatuses(List.of(cardId)))
                .thenReturn(Map.of(cardId, AgentActionStatus.CANCELLED));
        when(conversationStore.usage(me.getId())).thenReturn(4L);

        AgentChatHistoryResDto res = agentChatService.history(fridgeId);

        assertThat(res.getMessages()).extracting(AgentChatHistoryResDto.Message::getRole)
                .containsExactly("user", "assistant");
        AgentChatHistoryResDto.Message answer = res.getMessages().get(1);
        assertThat(answer.getText()).isEqualTo("두부를 버린 것으로 정리할까요?");
        assertThat(answer.getActions()).singleElement()
                .satisfies(card -> {
                    assertThat(card.getId()).isEqualTo(cardId);
                    assertThat(card.getStatus()).isEqualTo(AgentActionStatus.CANCELLED);
                    assertThat(card.getFoods()).extracting(FoodResDto::getName).containsExactly("두부");
                });
        assertThat(res.getRemainingToday()).isEqualTo(26);
        verify(anthropicClient.messages(), never()).create(any(MessageCreateParams.class));
    }

    @Test
    void 도구가_실패하면_오류_결과로_돌려줘서_Claude가_설명하게_한다() throws Exception {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenReturn(
                        message("tool_use", toolUse("toolu_1", "get_food_detail", "{\"foodId\":99}")),
                        message("end_turn", text("그 식품은 찾을 수 없어요.")));
        when(toolExecutor.execute(eq(fridgeId), eq(Type.COMMON), eq("get_food_detail"), any()))
                .thenReturn(new ToolOutcome("{\"error\":\"식품을 찾을 수 없습니다. ID: [99]\"}", true, null));

        AgentChatResDto res = agentChatService.chat(fridgeId, "99번 식품 알려줘");

        assertThat(res.getMessage()).isEqualTo("그 식품은 찾을 수 없어요.");
        assertThat(body(sentRequests(2).get(1))).contains("\"is_error\":true");
    }

    @Test
    void 이전_대화와_오늘_날짜_권한을_함께_보낸다() throws Exception {
        loginAs(Type.MANAGER);
        when(conversationStore.load(fridgeId, me.getId()))
                .thenReturn(List.of(Turn.user("우유 있어?"), Turn.assistant("우유 1개 있어요.", List.of())));
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenReturn(message("end_turn", text("네, 그 우유예요.")));

        agentChatService.chat(fridgeId, "그거 언제까지야?");

        String request = body(sentRequests(1).get(0));
        assertThat(request).contains("우유 있어?").contains("우유 1개 있어요.").contains("그거 언제까지야?");
        assertThat(request).contains("냉장고 관리자").contains("지금: ").contains("사용자 이름: 홍길동");
        // 도구 4개를 모두 strict로 보낸다
        assertThat(request).contains("find_foods").contains("get_food_detail")
                .contains("propose_finish").contains("propose_delete").contains("\"strict\":true");
    }

    @Test
    void 하루_사용_횟수를_넘으면_Claude를_부르지_않는다() {
        when(conversationStore.increaseUsage(me.getId())).thenReturn(31L);

        assertThatThrownBy(() -> agentChatService.chat(fridgeId, "우유 있어?"))
                .isInstanceOf(AgentChatException.class)
                .satisfies(e -> assertThat(((AgentChatException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(anthropicClient.messages(), never()).create(any(MessageCreateParams.class));
        verify(conversationStore, never()).append(anyLong(), any(), anyList(), anyString(), anyString(), anyList());
    }

    @Test
    void 도구만_계속_부르면_6번에서_멈추고_안내한다() throws Exception {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenReturn(message("tool_use", toolUse("toolu_x", "find_foods", "{}")));
        when(toolExecutor.execute(any(), any(), any(), any()))
                .thenReturn(new ToolOutcome("{\"total\":0,\"foods\":[]}", false, null));

        AgentChatResDto res = agentChatService.chat(fridgeId, "다 찾아줘");

        assertThat(res.getMessage()).isEqualTo(AgentChatService.GAVE_UP_MESSAGE);
        sentRequests(AgentChatService.MAX_ROUNDS);
    }

    @Test
    void 거절되면_502로_알리고_대화를_저장하지_않는다() throws Exception {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenReturn(message("refusal"));

        assertThatThrownBy(() -> agentChatService.chat(fridgeId, "이상한 요청"))
                .isInstanceOf(AiException.class)
                .satisfies(e -> assertThat(((AiException) e).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verify(conversationStore, never()).append(anyLong(), any(), anyList(), anyString(), anyString(), anyList());
    }

    private AgentActionResDto tofuCard(UUID id) {
        return AgentActionResDto.builder()
                .id(id).type(AgentActionType.FINISH_DISCARDED).status(AgentActionStatus.PENDING)
                .foods(List.of(FoodResDto.builder().id(12L).name("두부").build()))
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build();
    }

    private void loginAs(Type type) {
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, me, type));
    }

    private List<MessageCreateParams> sentRequests(int count) {
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(anthropicClient.messages(), times(count)).create(captor.capture());
        return captor.getAllValues();
    }

    private String body(MessageCreateParams params) throws Exception {
        return ObjectMappers.jsonMapper().writeValueAsString(params._body());
    }

    private String text(String text) {
        return "{\"type\":\"text\",\"text\":\"" + text + "\"}";
    }

    private String toolUse(String id, String name, String inputJson) {
        return "{\"type\":\"tool_use\",\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"input\":" + inputJson + "}";
    }

    // 실제 API 응답과 같은 모양의 JSON 으로 Message 를 만든다
    private Message message(String stopReason, String... blocks) throws Exception {
        String json = """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5",
                 "content":[%s],"stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """.formatted(String.join(",", blocks), stopReason);
        return ObjectMappers.jsonMapper().readValue(json, Message.class);
    }
}

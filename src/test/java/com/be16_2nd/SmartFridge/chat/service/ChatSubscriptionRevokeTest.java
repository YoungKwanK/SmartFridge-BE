package com.be16_2nd.SmartFridge.chat.service;

import com.be16_2nd.SmartFridge.chat.domain.ChatRoomType;
import com.be16_2nd.SmartFridge.chat.repository.PurchasePostRepository;
import com.be16_2nd.SmartFridge.fridge.dto.FridgeCreateDto;
import com.be16_2nd.SmartFridge.fridge.repository.FridgeRepository;
import com.be16_2nd.SmartFridge.fridge.service.FridgeService;
import com.be16_2nd.SmartFridge.member.domain.SocialType;
import com.be16_2nd.SmartFridge.member.service.MemberService;
import com.be16_2nd.SmartFridge.post.service.PostService;
import com.be16_2nd.SmartFridge.support.PurchaseRoomFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 채팅방에서 빠진 회원의 STOMP 구독이 끊기는지 확인
 * - 나가기 / 관리자 위임 때 구독 해제 이벤트가 나가는지
 * - 이벤트를 받으면 그 회원의 구독만 브로커와 접속 목록(Redis)에서 지워지는지
 */
@SpringBootTest
@Transactional
@RecordApplicationEvents
class ChatSubscriptionRevokeTest {

    // DB에 없는 방 id (브로커·Redis만 쓰는 테스트용)
    static final long ROOM = 990_101L;
    static final String DESTINATION = "/topic/chat/PURCHASE/" + ROOM;

    @Autowired ApplicationEvents events;
    @Autowired MemberService memberService;
    @Autowired FridgeService fridgeService;
    @Autowired FridgeRepository fridgeRepository;
    @Autowired ChatService chatService;
    @Autowired PostService postService;
    @Autowired PurchasePostRepository purchasePostRepository;
    @Autowired ChatPresence chatPresence;
    @Autowired ChatSubscriptionRevoker revoker;
    @Autowired SimpleBrokerMessageHandler broker;
    @Autowired @Qualifier("brokerChannel") MessageChannel brokerChannel;
    @Autowired @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.delete(ChatPresence.key(ChatRoomType.PURCHASE, ROOM));
        SecurityContextHolder.clearContext();
    }

    @Test
    void 공동구매_방을_나가면_그_회원의_구독_해제_이벤트가_나간다() {
        Fixture f = purchaseRoomWithMember();

        login("b@r.com");
        chatService.leavePurchaseChatRoom(f.roomId());

        assertThat(revokedEvents()).containsExactly(new ChatSubscriptionRevoked(ChatRoomType.PURCHASE, f.roomId(), "b@r.com"));
    }

    @Test
    void 냉장고에서_강퇴되면_공동구매_방_구독_해제_이벤트가_나간다() {
        Fixture f = purchaseRoomWithMember();

        login("a@r.com");
        fridgeService.removeMember(f.fridgeId(), "b@r.com");

        assertThat(revokedEvents()).contains(new ChatSubscriptionRevoked(ChatRoomType.PURCHASE, f.roomId(), "b@r.com"));
    }

    @Test
    void 관리자를_위임하면_이전_관리자의_1대1_방_구독_해제_이벤트가_나간다() {
        Fixture f = purchaseRoomWithMember();

        login("a@r.com");
        fridgeService.delegateManager(f.fridgeId(), "b@r.com");

        assertThat(revokedEvents())
                .isNotEmpty()
                .allMatch(e -> e.type() == ChatRoomType.MANAGER && e.email().equals("a@r.com"));
    }

    @Test
    void 해제_이벤트를_받으면_그_회원의_구독만_브로커와_접속_목록에서_지워진다() throws InterruptedException {
        subscribe("s-b", "sub-0", "b@r.com");
        subscribe("s-b2", "sub-3", "b@r.com");   // 같은 회원의 다른 탭
        subscribe("s-c", "sub-0", "c@r.com");

        revoker.revoke(new ChatSubscriptionRevoked(ChatRoomType.PURCHASE, ROOM, "b@r.com"));

        // 브로커 해제는 Redis 채널을 거쳐 비동기로 처리된다
        MultiValueMap<String, String> remaining = brokerSubscriptions();
        for (int i = 0; i < 50 && remaining.size() > 1; i++) {
            Thread.sleep(100);
            remaining = brokerSubscriptions();
        }
        assertThat(remaining.keySet()).containsExactly("s-c");
        assertThat(chatPresence.onlineEmails(ChatRoomType.PURCHASE, ROOM)).containsExactly("c@r.com");
    }

    @Test
    void 구독이_없는_회원이면_아무것도_지우지_않는다() {
        subscribe("s-c", "sub-0", "c@r.com");

        assertThat(chatPresence.removeSubscriptions(ChatRoomType.PURCHASE, ROOM, "b@r.com")).isEmpty();
        assertThat(chatPresence.onlineEmails(ChatRoomType.PURCHASE, ROOM)).containsExactly("c@r.com");
    }

    @Test
    void 구독_field는_세션_id와_구독_id로_나뉜다() {
        assertThat(ChatPresence.Subscription.parse("abc:sub-0")).isEqualTo(new ChatPresence.Subscription("abc", "sub-0"));
        assertThat(ChatPresence.Subscription.parse("abc")).isNull();
        assertThat(ChatPresence.Subscription.parse(":sub-0")).isNull();
        assertThat(ChatPresence.Subscription.parse("abc:")).isNull();
    }

    // a: 냉장고 관리자이자 공동구매 방장, b: 냉장고 멤버이자 공동구매 참여자
    private Fixture purchaseRoomWithMember() {
        memberService.socialLogin(SocialType.GOOGLE, "r-a", "a@r.com", true, "회원A", null);
        memberService.socialLogin(SocialType.GOOGLE, "r-b", "b@r.com", true, "회원B", null);
        login("a@r.com");
        Long fridgeId = fridgeService.create(FridgeCreateDto.builder().fridgeName("구독냉장고").build()).getFridgeId();
        String inviteCode = fridgeRepository.findById(fridgeId).orElseThrow().getInviteCode();
        Long roomId = PurchaseRoomFixture.openRoom(postService, purchasePostRepository, fridgeId, "공구", 3);
        login("b@r.com");
        fridgeService.joinFridge(inviteCode);
        chatService.addParticipantToPurchaseChat(roomId);
        return new Fixture(fridgeId, roomId);
    }

    private List<ChatSubscriptionRevoked> revokedEvents() {
        return events.stream(ChatSubscriptionRevoked.class).toList();
    }

    // StompHandler 가 SUBSCRIBE 때 하는 일: 브로커에 구독 등록 + 접속 목록 기록
    private void subscribe(String sessionId, String subscriptionId, String email) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(DESTINATION);
        brokerChannel.send(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
        chatPresence.subscribe(new HashMap<>(), sessionId, subscriptionId, ChatRoomType.PURCHASE, ROOM, email);
    }

    private MultiValueMap<String, String> brokerSubscriptions() {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(DESTINATION);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return broker.getSubscriptionRegistry().findSubscriptions(message);
    }

    private void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private record Fixture(Long fridgeId, Long roomId) {}
}

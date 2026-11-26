package com.be16_2nd.SmartFridge.chat.service;

import com.be16_2nd.SmartFridge.chat.domain.ChatMessage;
import com.be16_2nd.SmartFridge.chat.domain.ChatRoomType;
import com.be16_2nd.SmartFridge.chat.dto.ChatMessageDto;
import com.be16_2nd.SmartFridge.chat.repository.PurchasePostRepository;
import com.be16_2nd.SmartFridge.fridge.dto.FridgeCreateDto;
import com.be16_2nd.SmartFridge.fridge.service.FridgeService;
import com.be16_2nd.SmartFridge.member.domain.SocialType;
import com.be16_2nd.SmartFridge.member.repository.MemberRepository;
import com.be16_2nd.SmartFridge.member.service.MemberService;
import com.be16_2nd.SmartFridge.post.service.PostService;
import com.be16_2nd.SmartFridge.support.PurchaseRoomFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * STOMP 전송은 saveMessage 트랜잭션이 끝난 뒤 ChatMessageDto.fromEntity 로 발신자 이름을 꺼내 Redis 로 발행한다
 * 발신자가 초기화되지 않은 프록시로 남으면 LazyInitializationException 으로 발행 전에 멈춰 메시지가 실시간으로 전달되지 않는다
 * 트랜잭션 밖 동작을 봐야 하므로 @Transactional 을 붙이지 않는다 (데이터가 커밋됨)
 */
@SpringBootTest
class ChatSendSenderLoadTest {

    @Autowired MemberService memberService;
    @Autowired MemberRepository memberRepository;
    @Autowired FridgeService fridgeService;
    @Autowired ChatService chatService;
    @Autowired PostService postService;
    @Autowired PurchasePostRepository purchasePostRepository;

    final String sender = "sender-" + System.nanoTime() + "@load.com";
    UUID senderId;
    Long fridgeId;

    @BeforeEach
    void setUp() {
        memberService.socialLogin(SocialType.GOOGLE, "load-" + sender, sender, true, "보낸사람", null);
        senderId = memberRepository.findByEmail(sender).orElseThrow().getId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(senderId.toString(), null, List.of()));
        fridgeId = fridgeService.create(FridgeCreateDto.builder().fridgeName("발신자냉장고").build()).getFridgeId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 관리자_1대1_방에_저장한_메시지는_트랜잭션_밖에서도_발신자_이름을_꺼낼_수_있다() {
        Long roomId = chatService.getMyChatRooms(fridgeId).get(0).getRoomId();

        assertSenderLoaded(chatService.saveMessage(roomId, ChatRoomType.MANAGER, senderId,
                ChatMessageDto.builder().message("안녕하세요").build()));
    }

    @Test
    void 공동구매_방에_저장한_메시지는_트랜잭션_밖에서도_발신자_이름을_꺼낼_수_있다() {
        Long roomId = PurchaseRoomFixture.openRoom(postService, purchasePostRepository, fridgeId, "발신자", 3);

        assertSenderLoaded(chatService.saveMessage(roomId, ChatRoomType.PURCHASE, senderId,
                ChatMessageDto.builder().message("안녕하세요").build()));
    }

    private void assertSenderLoaded(ChatMessage saved) {
        ChatMessageDto dto = ChatMessageDto.fromEntity(saved);
        assertThat(dto.getSenderId()).isEqualTo(senderId);
        assertThat(dto.getSenderName()).isEqualTo("보낸사람");
    }
}

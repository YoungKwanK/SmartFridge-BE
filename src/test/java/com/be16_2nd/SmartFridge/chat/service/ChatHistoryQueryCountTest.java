package com.be16_2nd.SmartFridge.chat.service;

import com.be16_2nd.SmartFridge.chat.domain.ChatRoomType;
import com.be16_2nd.SmartFridge.chat.dto.ChatMessageDto;
import com.be16_2nd.SmartFridge.chat.repository.PurchasePostRepository;
import com.be16_2nd.SmartFridge.fridge.dto.FridgeCreateDto;
import com.be16_2nd.SmartFridge.fridge.repository.FridgeRepository;
import com.be16_2nd.SmartFridge.fridge.service.FridgeService;
import com.be16_2nd.SmartFridge.member.domain.SocialType;
import com.be16_2nd.SmartFridge.member.service.MemberService;
import com.be16_2nd.SmartFridge.post.service.PostService;
import com.be16_2nd.SmartFridge.support.PurchaseRoomFixture;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 채팅 내역 조회의 쿼리 수가 메시지·보낸 사람·이미지 수와 상관없이 일정한지 확인 (N+1 회귀 방지)
 */
@SpringBootTest
@Transactional
class ChatHistoryQueryCountTest {

    @Autowired MemberService memberService;
    @Autowired FridgeService fridgeService;
    @Autowired FridgeRepository fridgeRepository;
    @Autowired ChatService chatService;
    @Autowired PostService postService;
    @Autowired PurchasePostRepository purchasePostRepository;
    @Autowired S3Client s3Client;
    @Autowired EntityManager em;
    @Value("${cloud.aws.s3.bucket}") String bucket;

    Statistics statistics;
    String inviteCode;
    Long roomId;
    int memberSeq;

    @BeforeEach
    void setUp() {
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        memberService.socialLogin(SocialType.GOOGLE, "h-owner", "owner@h.com", true, "방장", null);
        login("owner@h.com");
        Long fridgeId = fridgeService.create(FridgeCreateDto.builder().fridgeName("내역냉장고").build()).getFridgeId();
        inviteCode = fridgeRepository.findById(fridgeId).orElseThrow().getInviteCode();
        roomId = PurchaseRoomFixture.openRoom(postService, purchasePostRepository, fridgeId, "공구", 20);
    }

    @AfterEach
    void tearDown() {
        statistics.setStatisticsEnabled(false);
        SecurityContextHolder.clearContext();
    }

    @Test
    void 채팅_내역은_보낸_사람과_이미지가_늘어도_쿼리_수가_같다() {
        addMembersWhoSendImageMessage(2);
        long small = countHistoryQueries();

        addMembersWhoSendImageMessage(6);
        long large = countHistoryQueries();

        assertThat(large).isEqualTo(small);
    }

    @Test
    void 채팅_내역에_보낸_사람과_이미지가_순서대로_담긴다() {
        addMembersWhoSendImageMessage(2);
        em.flush();
        em.clear();

        login("owner@h.com");
        Page<ChatMessageDto> page = chatService.getChatHistory(ChatRoomType.PURCHASE, roomId, latestFirst());

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(ChatMessageDto::getSenderEmail)
                .containsExactly("member1@h.com", "member0@h.com");
        assertThat(page.getContent()).allSatisfy(dto -> {
            assertThat(dto.getSenderName()).startsWith("회원");
            assertThat(dto.getImageUrls()).hasSize(2);
            assertThat(dto.getImageUrls().get(0)).endsWith("-0.jpg");
            assertThat(dto.getImageUrls().get(1)).endsWith("-1.jpg");
        });
    }

    // 새 회원이 방에 참여해서 이미지 2장짜리 메시지를 보낸다 (보낸 사람마다 다른 회원)
    private void addMembersWhoSendImageMessage(int count) {
        for (int i = 0; i < count; i++) {
            String email = "member" + memberSeq + "@h.com";
            memberService.socialLogin(SocialType.GOOGLE, "h-" + memberSeq, email, true, "회원" + memberSeq, null);
            memberSeq++;
            login(email);
            fridgeService.joinFridge(inviteCode);
            chatService.addParticipantToPurchaseChat(roomId);
            String name = UUID.randomUUID().toString();
            chatService.saveMessage(roomId, ChatRoomType.PURCHASE, email, ChatMessageDto.builder()
                    .message("사진")
                    .imageUrls(List.of(imageUrl(name + "-0.jpg"), imageUrl(name + "-1.jpg")))
                    .build());
        }
    }

    private long countHistoryQueries() {
        login("owner@h.com");
        em.flush();
        em.clear();
        statistics.clear();
        chatService.getChatHistory(ChatRoomType.PURCHASE, roomId, latestFirst()).getContent();
        return statistics.getPrepareStatementCount();
    }

    private PageRequest latestFirst() {
        return PageRequest.of(0, 40, Sort.by(Sort.Direction.DESC, "createdTime", "id"));
    }

    // 이 채팅방 경로로 업로드된 이미지 URL (ChatImageService.validateImageUrls 통과)
    private String imageUrl(String fileName) {
        String key = ChatImageService.keyPrefix(ChatRoomType.PURCHASE, roomId) + fileName;
        return s3Client.utilities().getUrl(b -> b.bucket(bucket).key(key)).toExternalForm();
    }

    private void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }
}

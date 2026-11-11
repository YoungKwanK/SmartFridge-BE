package com.be16_2nd.SmartFridge.chat.service;

import com.be16_2nd.SmartFridge.chat.domain.ChatRoomType;
import com.be16_2nd.SmartFridge.chat.dto.ChatMessageDto;
import com.be16_2nd.SmartFridge.chat.repository.PurchasePostRepository;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.dto.FridgeCreateDto;
import com.be16_2nd.SmartFridge.fridge.repository.FridgeRepository;
import com.be16_2nd.SmartFridge.fridge.service.FridgeService;
import com.be16_2nd.SmartFridge.member.domain.SocialType;
import com.be16_2nd.SmartFridge.member.repository.MemberRepository;
import com.be16_2nd.SmartFridge.member.service.MemberService;
import com.be16_2nd.SmartFridge.post.service.PostService;
import com.be16_2nd.SmartFridge.support.PurchaseRoomFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 채팅방이 삭제될 때 그 방의 메시지 이미지가 S3 삭제 대상으로 모이는지 확인
 * (실제 삭제는 커밋 뒤라 테스트 트랜잭션에서는 일어나지 않는다 → 이벤트에 담긴 URL로 확인, 삭제 동작은 ChatImageServiceTest)
 */
@SpringBootTest
@Transactional
@RecordApplicationEvents
class ChatImageCleanupTest {

    @Autowired ApplicationEvents events;
    @Autowired MemberService memberService;
    @Autowired MemberRepository memberRepository;
    @Autowired FridgeService fridgeService;
    @Autowired FridgeRepository fridgeRepository;
    @Autowired ChatService chatService;
    @Autowired ManagerChatRoomLifecycle managerChatRoomLifecycle;
    @Autowired PostService postService;
    @Autowired PurchasePostRepository purchasePostRepository;
    @Autowired S3Client s3Client;
    @Autowired EntityManager em;
    @Value("${cloud.aws.s3.bucket}") String bucket;

    Long fridgeId;
    Long roomId;

    // a: 냉장고 관리자이자 공동구매 방장, b: 냉장고 멤버이자 공동구매 참여자
    @BeforeEach
    void setUp() {
        memberService.socialLogin(SocialType.GOOGLE, "i-a", "a@i.com", true, "회원A", null);
        memberService.socialLogin(SocialType.GOOGLE, "i-b", "b@i.com", true, "회원B", null);
        login("a@i.com");
        fridgeId = fridgeService.create(FridgeCreateDto.builder().fridgeName("이미지냉장고").build()).getFridgeId();
        String inviteCode = fridgeRepository.findById(fridgeId).orElseThrow().getInviteCode();
        roomId = PurchaseRoomFixture.openRoom(postService, purchasePostRepository, fridgeId, "공구", 3);
        login("b@i.com");
        fridgeService.joinFridge(inviteCode);
        chatService.addParticipantToPurchaseChat(roomId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 방장이_나가서_공동구매_방이_지워지면_그_방의_이미지가_삭제_대상이_된다() {
        String fromA = sendImage(ChatRoomType.PURCHASE, roomId, "a@i.com");
        String fromB = sendImage(ChatRoomType.PURCHASE, roomId, "b@i.com");

        newRequest();
        login("a@i.com");
        chatService.leavePurchaseChatRoom(roomId);

        assertThat(deletedUrls()).containsExactlyInAnyOrder(fromA, fromB);
    }

    @Test
    void 참여자만_나가면_방이_남으므로_이미지를_지우지_않는다() {
        sendImage(ChatRoomType.PURCHASE, roomId, "b@i.com");

        newRequest();
        login("b@i.com");
        chatService.leavePurchaseChatRoom(roomId);

        assertThat(deletedUrls()).isEmpty();
    }

    @Test
    void 냉장고를_나가면_1대1_방과_공동구매_방_이미지가_삭제_대상이_된다() {
        Long managerRoomId = managerRoomIdOf("b@i.com");
        String managerImage = sendImage(ChatRoomType.MANAGER, managerRoomId, "b@i.com");
        login("b@i.com");
        Long myRoomId = PurchaseRoomFixture.openRoom(postService, purchasePostRepository, fridgeId, "내 공구", 3);
        String myRoomImage = sendImage(ChatRoomType.PURCHASE, myRoomId, "b@i.com");
        sendImage(ChatRoomType.PURCHASE, roomId, "b@i.com");   // a의 방은 남는다

        newRequest();
        login("b@i.com");
        fridgeService.leaveFridge(fridgeId);

        assertThat(deletedUrls()).containsExactlyInAnyOrder(managerImage, myRoomImage);
    }

    @Test
    void 냉장고를_삭제하면_모든_채팅방_이미지가_삭제_대상이_된다() {
        String purchaseImage = sendImage(ChatRoomType.PURCHASE, roomId, "b@i.com");
        String managerImage = sendImage(ChatRoomType.MANAGER, managerRoomIdOf("b@i.com"), "b@i.com");

        newRequest();
        login("a@i.com");
        fridgeService.deleteFridge(fridgeId);

        assertThat(deletedUrls()).containsExactlyInAnyOrder(purchaseImage, managerImage);
    }

    @Test
    void 이미지가_없는_방이_지워지면_이벤트가_나가지_않는다() {
        login("a@i.com");
        chatService.leavePurchaseChatRoom(roomId);

        assertThat(events.stream(ChatImageCleanup.ChatImagesDeleted.class)).isEmpty();
    }

    // 실제로는 메시지 전송과 방 삭제가 다른 요청(다른 영속성 컨텍스트)에서 일어난다
    private void newRequest() {
        em.flush();
        em.clear();
    }

    private List<String> deletedUrls() {
        return events.stream(ChatImageCleanup.ChatImagesDeleted.class)
                .flatMap(e -> e.imageUrls().stream())
                .toList();
    }

    private Long managerRoomIdOf(String email) {
        Fridge fridge = fridgeRepository.findById(fridgeId).orElseThrow();
        return managerChatRoomLifecycle.findManagerChatRoom(fridge, memberRepository.findByEmail(email).orElseThrow())
                .orElseThrow().getId();
    }

    // 이 방 경로로 업로드된 이미지 1장짜리 메시지를 보내고 그 URL 을 돌려준다
    private String sendImage(ChatRoomType type, Long targetRoomId, String senderEmail) {
        String key = ChatImageService.keyPrefix(type, targetRoomId) + UUID.randomUUID() + ".jpg";
        String url = s3Client.utilities().getUrl(b -> b.bucket(bucket).key(key)).toExternalForm();
        chatService.saveMessage(targetRoomId, type, senderEmail,
                ChatMessageDto.builder().imageUrls(List.of(url)).build());
        return url;
    }

    private void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }
}

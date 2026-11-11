package com.be16_2nd.SmartFridge.chat.service;

import com.be16_2nd.SmartFridge.chat.domain.ChatMessage;
import com.be16_2nd.SmartFridge.chat.domain.ChatRoomType;
import com.be16_2nd.SmartFridge.chat.domain.MessageType;
import com.be16_2nd.SmartFridge.chat.dto.ChatMessageDto;
import com.be16_2nd.SmartFridge.chat.repository.ChatMessageRepository;
import com.be16_2nd.SmartFridge.chat.repository.PurchasePostRepository;
import com.be16_2nd.SmartFridge.fridge.dto.FridgeCreateDto;
import com.be16_2nd.SmartFridge.fridge.service.FridgeService;
import com.be16_2nd.SmartFridge.member.domain.SocialType;
import com.be16_2nd.SmartFridge.member.service.MemberService;
import com.be16_2nd.SmartFridge.post.service.PostService;
import com.be16_2nd.SmartFridge.support.PurchaseRoomFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * 채팅 메시지 내용 검증: 빈 메시지 거부, 글자 수 상한
 */
@SpringBootTest
@Transactional
class ChatMessageValidationTest {

    static final String SENDER = "owner@v.com";

    @Autowired MemberService memberService;
    @Autowired FridgeService fridgeService;
    @Autowired ChatService chatService;
    @Autowired PostService postService;
    @Autowired PurchasePostRepository purchasePostRepository;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired S3Client s3Client;
    @Value("${cloud.aws.s3.bucket}") String bucket;

    Long roomId;

    @BeforeEach
    void setUp() {
        memberService.socialLogin(SocialType.GOOGLE, "v-owner", SENDER, true, "방장", null);
        login(SENDER);
        Long fridgeId = fridgeService.create(FridgeCreateDto.builder().fridgeName("검증냉장고").build()).getFridgeId();
        roomId = PurchaseRoomFixture.openRoom(postService, purchasePostRepository, fridgeId, "공구", 3);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t"})
    void 글도_이미지도_없는_메시지는_저장하지_않는다(String contents) {
        long before = chatMessageRepository.count();

        assertThatThrownBy(() -> send(contents, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("빈 메시지");
        assertThat(chatMessageRepository.count()).isEqualTo(before);
    }

    @Test
    void 이미지만_있으면_글이_없어도_보낼_수_있다() {
        ChatMessage message = send("", List.of(imageUrl("a.jpg")));

        assertThat(message.getMessageType()).isEqualTo(MessageType.IMAGE);
        assertThat(message.getChatMessageImages()).hasSize(1);
    }

    @Test
    void 글자_수_상한까지는_보낼_수_있고_넘으면_거부한다() {
        String max = "가".repeat(ChatMessage.MAX_CONTENTS_LENGTH);

        assertThat(send(max, List.of()).getContents()).hasSize(ChatMessage.MAX_CONTENTS_LENGTH);
        assertThatThrownBy(() -> send(max + "가", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ChatMessage.MAX_CONTENTS_LENGTH + "자");
    }

    private ChatMessage send(String contents, List<String> imageUrls) {
        return chatService.saveMessage(roomId, ChatRoomType.PURCHASE, SENDER,
                ChatMessageDto.builder().message(contents).imageUrls(imageUrls).build());
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

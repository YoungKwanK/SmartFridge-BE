package com.be16_2nd.SmartFridge.chat.service;

import com.be16_2nd.SmartFridge.chat.domain.ChatMessage;
import com.be16_2nd.SmartFridge.chat.domain.ChatRoomType;
import com.be16_2nd.SmartFridge.chat.dto.ChatMessageDto;
import com.be16_2nd.SmartFridge.chat.repository.ChatMessageRepository;
import com.be16_2nd.SmartFridge.chat.repository.ChatParticipantRepository;
import com.be16_2nd.SmartFridge.chat.repository.ChatRoomRepository;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 같은 채팅방에 메시지가 동시에 들어와도 모두 저장되는지 확인 (교착 상태로 롤백되어 사라지면 안 된다)
 * 실제 트랜잭션을 동시에 실행해야 하므로 @Transactional 을 붙이지 않는다 (데이터가 커밋됨)
 */
@SpringBootTest
class ChatSendConcurrencyTest {

    static final int MESSAGES = 12;

    @Autowired MemberService memberService;
    @Autowired FridgeService fridgeService;
    @Autowired FridgeRepository fridgeRepository;
    @Autowired ChatService chatService;
    @Autowired PostService postService;
    @Autowired PurchasePostRepository purchasePostRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired ChatParticipantRepository chatParticipantRepository;
    @Autowired TransactionTemplate transactionTemplate;

    final String run = Long.toString(System.nanoTime());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 같은_방에_동시에_보낸_메시지가_모두_저장된다() throws Exception {
        // 방 참여자 3명 (개설자 + 2명)
        List<String> senders = List.of(email("creator"), email("m1"), email("m2"));
        for (int i = 0; i < senders.size(); i++) {
            memberService.socialLogin(SocialType.GOOGLE, "s" + i + "-" + run, senders.get(i), true, "발신자" + i, null);
        }
        login(senders.get(0));
        Long fridgeId = fridgeService.create(FridgeCreateDto.builder().fridgeName("동시전송냉장고").build()).getFridgeId();
        String inviteCode = fridgeRepository.findById(fridgeId).orElseThrow().getInviteCode();
        for (String member : senders.subList(1, senders.size())) {
            login(member);
            fridgeService.joinFridge(inviteCode);
        }
        login(senders.get(0));
        Long roomId = PurchaseRoomFixture.openRoom(postService, purchasePostRepository, fridgeId, "동시 전송", 10);
        for (String member : senders.subList(1, senders.size())) {
            login(member);
            chatService.addParticipantToPurchaseChat(roomId);
        }
        SecurityContextHolder.clearContext();

        // 모든 스레드가 준비된 뒤 한꺼번에 전송
        ExecutorService pool = Executors.newFixedThreadPool(MESSAGES);
        CountDownLatch ready = new CountDownLatch(MESSAGES);
        CountDownLatch start = new CountDownLatch(1);
        Map<String, List<Long>> sentIds = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < MESSAGES; i++) {
            String sender = senders.get(i % senders.size());
            String text = "메시지 " + i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                ChatMessage saved = chatService.saveMessage(roomId, ChatRoomType.PURCHASE, sender,
                        ChatMessageDto.builder().message(text).build());
                sentIds.computeIfAbsent(sender, k -> new CopyOnWriteArrayList<>()).add(saved.getId());
                return null;
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        // 교착 상태로 롤백되면 여기서 예외가 터진다
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        long stored = transactionTemplate.execute(s -> chatMessageRepository
                .findByChatRoom(chatRoomRepository.getReferenceById(roomId), PageRequest.of(0, 1))
                .getTotalElements());
        assertThat(stored).isEqualTo(MESSAGES);

        // 보낸 사람의 읽음 커서가 뒤로 밀리지 않는다 (자기가 보낸 마지막 메시지까지 읽음)
        for (String sender : senders) {
            Long lastSent = sentIds.get(sender).stream().max(Long::compare).orElseThrow();
            Long lastRead = transactionTemplate.execute(s -> chatParticipantRepository
                    .findByChatRoomIdAndMemberEmail(roomId, sender).orElseThrow().getLastReadMessageId());
            assertThat(lastRead).isGreaterThanOrEqualTo(lastSent);
        }
    }

    private String email(String name) {
        return name + "-" + run + "@concurrency.com";
    }

    private void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }
}

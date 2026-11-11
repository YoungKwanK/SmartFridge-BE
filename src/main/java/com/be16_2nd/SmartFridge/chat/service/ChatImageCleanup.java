package com.be16_2nd.SmartFridge.chat.service;

import com.be16_2nd.SmartFridge.chat.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/*
 * 채팅방이 삭제될 때 그 방의 메시지 이미지를 S3에서 지운다
 * - 방을 지우기 전에 이미지 URL 을 모아 두고(지운 뒤에는 DB에서 찾을 수 없음), 트랜잭션이 커밋된 뒤에만 지운다
 *   (롤백되면 메시지는 남는데 이미지만 사라지는 일을 막는다)
 * - 버킷 목록 조회 권한(s3:ListBucket) 없이 동작하도록 경로 단위가 아니라 DB에 저장된 URL 기준으로 지운다
 */
@Component
@RequiredArgsConstructor
public class ChatImageCleanup {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatImageService chatImageService;
    private final ApplicationEventPublisher eventPublisher;

    // 채팅방 하나를 지우기 전에 호출
    public void beforeRoomDeleted(Long roomId) {
        publish(chatMessageRepository.findImageUrlsByRoomId(roomId));
    }

    // 냉장고를 지우기 전에 호출 (냉장고의 모든 채팅방)
    public void beforeFridgeDeleted(Long fridgeId) {
        publish(chatMessageRepository.findImageUrlsByFridgeId(fridgeId));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleteAfterCommit(ChatImagesDeleted event) {
        chatImageService.deleteImages(event.imageUrls());
    }

    private void publish(List<String> imageUrls) {
        if (!imageUrls.isEmpty()) {
            eventPublisher.publishEvent(new ChatImagesDeleted(imageUrls));
        }
    }

    public record ChatImagesDeleted(List<String> imageUrls) {}
}

package com.be16_2nd.SmartFridge.support;

import com.be16_2nd.SmartFridge.chat.repository.PurchasePostRepository;
import com.be16_2nd.SmartFridge.post.domain.PostCategory;
import com.be16_2nd.SmartFridge.post.dto.PostCreateDto;
import com.be16_2nd.SmartFridge.post.service.PostService;

/**
 * 테스트용 공동구매 채팅방 생성. 공동구매 방은 게시판 공동구매 글 작성으로만 생기므로 글을 쓰고 그 방 id 를 돌려준다
 * (현재 로그인한 회원이 작성자 = 방장)
 */
public final class PurchaseRoomFixture {

    private PurchaseRoomFixture() {
    }

    public static Long openRoom(PostService postService, PurchasePostRepository purchasePostRepository,
                                Long fridgeId, String title, int maxParticipants) {
        Long postId = postService.createPost(fridgeId, PostCreateDto.builder()
                .title(title).content("").category(PostCategory.공동구매).maxParticipants(maxParticipants).build());
        return purchasePostRepository.findByPostId(postId).orElseThrow().getChatRoom().getId();
    }
}

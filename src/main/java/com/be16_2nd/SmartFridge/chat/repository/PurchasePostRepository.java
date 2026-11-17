package com.be16_2nd.SmartFridge.chat.repository;

import com.be16_2nd.SmartFridge.chat.domain.PurchasePost;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchasePostRepository extends JpaRepository<PurchasePost, Long> {
    Optional<PurchasePost> findByChatRoomId(Long chatRoomId);
    Optional<PurchasePost> findByPostId(Long postId);

    // 게시판 목록 한 페이지의 공동구매 모집 현황. 참여자 수·참여 여부 계산에 쓰는 참여자까지 한 번에 가져온다
    @EntityGraph(attributePaths = {"chatRoom", "chatRoom.participants", "chatRoom.participants.member", "creator", "post"})
    List<PurchasePost> findByPostIdIn(Collection<Long> postIds);
}

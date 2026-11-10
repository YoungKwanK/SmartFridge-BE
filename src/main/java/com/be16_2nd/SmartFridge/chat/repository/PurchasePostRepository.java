package com.be16_2nd.SmartFridge.chat.repository;

import com.be16_2nd.SmartFridge.chat.domain.PurchasePost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PurchasePostRepository extends JpaRepository<PurchasePost, Long> {
    Optional<PurchasePost> findByChatRoomId(Long chatRoomId);
    Optional<PurchasePost> findByPostId(Long postId);
}

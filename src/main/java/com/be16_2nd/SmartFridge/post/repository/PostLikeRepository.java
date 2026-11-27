package com.be16_2nd.SmartFridge.post.repository;

import com.be16_2nd.SmartFridge.post.domain.Post;
import com.be16_2nd.SmartFridge.post.domain.PostLike;
import com.be16_2nd.SmartFridge.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByMemberAndPost(Member member, Post post);
    Optional<PostLike> findByMemberAndPost(Member member, Post post);

    Long countByPostMemberId(UUID memberId);

    // Redis에 좋아요 목록이 없을 때 DB에 쌓인 기록으로 다시 채운다
    @Query("SELECT pl.member.id FROM PostLike pl WHERE pl.post.id = :postId")
    List<UUID> findMemberIdsByPostId(@Param("postId") Long postId);
}

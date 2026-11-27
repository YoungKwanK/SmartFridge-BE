package com.be16_2nd.SmartFridge.post.repository;

import com.be16_2nd.SmartFridge.post.domain.Post;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.member.domain.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findAll(Specification<Post> specification, Pageable pageable);
    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    // Redis에 조회수가 없을 때 DB에 쌓인 값으로 다시 채운다
    @Query("SELECT p.viewCount FROM Post p WHERE p.id = :id")
    Optional<Integer> findViewCountById(@Param("id") Long id);
    void deleteAllByFridgeAndMember(Fridge fridge, Member member);

    List<Post> findAllByMemberId(UUID id);

    Long countByMemberId(UUID memberId);
}

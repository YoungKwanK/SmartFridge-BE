package com.be16_2nd.SmartFridge.common.service;

import com.be16_2nd.SmartFridge.post.repository.PostLikeRepository;
import com.be16_2nd.SmartFridge.post.repository.PostRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 게시글 조회수·좋아요. 화면에 보이는 값은 Redis에서 바로 읽고 쓰고, DB에는 RabbitMQ로 나중에 반영한다.
 * Redis가 비었을 때(재시작·초기화, DB에 직접 넣은 데이터) DB에 기록이 있는데도 0으로 보이지 않도록,
 * 키가 없으면 DB에 쌓인 값으로 먼저 채운 뒤 읽거나 바꾼다.
 */
@Component
public class PostStatsService {
    private final RedisTemplate<String, Long> viewTemplate;
    private final RedisTemplate<String, String> likeTemplate;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;

    public PostStatsService(@Qualifier("postViewStats") RedisTemplate<String, Long> viewTemplate,
                            @Qualifier("postLikeStats") RedisTemplate<String, String> likeTemplate,
                            PostRepository postRepository,
                            PostLikeRepository postLikeRepository) {
        this.viewTemplate = viewTemplate;
        this.likeTemplate = likeTemplate;
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
    }

    public void incrementViewCount(Long postId) {
        loadViewCountIfAbsent(postId);
        viewTemplate.opsForValue().increment(viewKey(postId));
    }

    public Long getViewCount(Long postId) {
        loadViewCountIfAbsent(postId);
        Long viewCount = viewTemplate.opsForValue().get(viewKey(postId));
        return viewCount == null ? 0L : viewCount;
    }

    // 좋아요를 누르거나 취소할 때도 먼저 채워야 한다. 비어 있는 채로 넣으면 나 한 명만 든 목록이 되어
    // 키가 생긴 뒤로는 DB의 나머지 기록을 다시 읽지 않는다
    public void addLike(Long postId, UUID memberId) {
        loadLikesIfAbsent(postId);
        likeTemplate.opsForSet().add(likeKey(postId), String.valueOf(memberId));
    }

    public void removeLike(Long postId, UUID memberId) {
        loadLikesIfAbsent(postId);
        likeTemplate.opsForSet().remove(likeKey(postId), String.valueOf(memberId));
    }

    public Long getLikeCount(Long postId) {
        loadLikesIfAbsent(postId);
        Long size = likeTemplate.opsForSet().size(likeKey(postId));
        return size == null ? 0L : size;
    }

    public boolean isLiked(Long postId, UUID memberId) {
        loadLikesIfAbsent(postId);
        return Boolean.TRUE.equals(likeTemplate.opsForSet().isMember(likeKey(postId), String.valueOf(memberId)));
    }

    // 키가 없을 때만 DB 값으로 채운다. 동시에 들어와도 SETNX라 먼저 채운 값이 남고, 그 뒤 INCR은 그대로 쌓인다
    private void loadViewCountIfAbsent(Long postId) {
        String key = viewKey(postId);
        if (Boolean.TRUE.equals(viewTemplate.hasKey(key))) {
            return;
        }
        long saved = postRepository.findViewCountById(postId).orElse(0);
        viewTemplate.opsForValue().setIfAbsent(key, saved);
    }

    // Redis는 빈 Set을 저장하지 않아서 좋아요가 0개인 글은 매번 DB를 한 번 확인한다 (post_id 조건이라 가볍다)
    private void loadLikesIfAbsent(Long postId) {
        String key = likeKey(postId);
        if (Boolean.TRUE.equals(likeTemplate.hasKey(key))) {
            return;
        }
        List<UUID> memberIds = postLikeRepository.findMemberIdsByPostId(postId);
        if (!memberIds.isEmpty()) {
            likeTemplate.opsForSet().add(key, memberIds.stream().map(String::valueOf).toArray(String[]::new));
        }
    }

    private static String viewKey(Long postId) {
        return postId + ":views";
    }

    private static String likeKey(Long postId) {
        return postId + ":likes";
    }
}

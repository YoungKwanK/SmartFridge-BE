package com.be16_2nd.SmartFridge.common.service;

import com.be16_2nd.SmartFridge.post.repository.PostLikeRepository;
import com.be16_2nd.SmartFridge.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 게시글 조회수·좋아요 단위 테스트. Redis가 비었을 때 DB에 쌓인 값으로 다시 채우는지 확인한다.
 * Redis와 DB는 가짜 객체(Mock)로 두므로 로컬 Redis·DB에 영향이 없다.
 */
@ExtendWith(MockitoExtension.class)
class PostStatsServiceTest {

    @Mock RedisTemplate<String, Long> viewTemplate;
    @Mock RedisTemplate<String, String> likeTemplate;
    @Mock ValueOperations<String, Long> views;
    @Mock SetOperations<String, String> likes;
    @Mock PostRepository postRepository;
    @Mock PostLikeRepository postLikeRepository;

    PostStatsService service;

    final Long postId = 7L;
    final UUID me = UUID.randomUUID();
    final UUID other = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(viewTemplate.opsForValue()).thenReturn(views);
        lenient().when(likeTemplate.opsForSet()).thenReturn(likes);
        service = new PostStatsService(viewTemplate, likeTemplate, postRepository, postLikeRepository);
    }

    // ---- 좋아요 ----

    @Test
    void Redis에_좋아요가_없으면_DB_기록으로_채운_뒤_개수와_내_좋아요를_보여준다() {
        when(likeTemplate.hasKey("7:likes")).thenReturn(false);
        when(postLikeRepository.findMemberIdsByPostId(postId)).thenReturn(List.of(me, other));
        when(likes.size("7:likes")).thenReturn(2L);

        assertThat(service.getLikeCount(postId)).isEqualTo(2L);

        verify(likes).add("7:likes", me.toString(), other.toString());
    }

    @Test
    void Redis에_좋아요가_있으면_DB를_읽지_않는다() {
        when(likeTemplate.hasKey("7:likes")).thenReturn(true);
        when(likes.isMember("7:likes", me.toString())).thenReturn(true);

        assertThat(service.isLiked(postId, me)).isTrue();

        verifyNoInteractions(postLikeRepository);
    }

    @Test
    void 비어_있을_때_좋아요를_누르면_DB_기록을_먼저_채우고_내_좋아요를_더한다() {
        // 먼저 채우지 않으면 나 한 명만 든 목록이 생겨 DB의 다른 좋아요가 영영 빠진다
        when(likeTemplate.hasKey("7:likes")).thenReturn(false);
        when(postLikeRepository.findMemberIdsByPostId(postId)).thenReturn(List.of(other));

        service.addLike(postId, me);

        InOrder order = inOrder(likes);
        order.verify(likes).add("7:likes", other.toString());
        order.verify(likes).add("7:likes", me.toString());
    }

    @Test
    void 좋아요가_하나도_없는_글은_빈_목록을_넣지_않고_0개로_보여준다() {
        when(likeTemplate.hasKey("7:likes")).thenReturn(false);
        when(postLikeRepository.findMemberIdsByPostId(postId)).thenReturn(List.of());
        when(likes.size("7:likes")).thenReturn(0L);

        assertThat(service.getLikeCount(postId)).isZero();

        verify(likes, never()).add(anyString(), any(String[].class));
    }

    // ---- 조회수 ----

    @Test
    void Redis에_조회수가_없으면_DB_값으로_채운_뒤_하나_더한다() {
        when(viewTemplate.hasKey("7:views")).thenReturn(false);
        when(postRepository.findViewCountById(postId)).thenReturn(Optional.of(41));

        service.incrementViewCount(postId);

        InOrder order = inOrder(views);
        order.verify(views).setIfAbsent("7:views", 41L);
        order.verify(views).increment("7:views");
    }

    @Test
    void Redis에_조회수가_있으면_DB를_읽지_않고_그대로_보여준다() {
        when(viewTemplate.hasKey("7:views")).thenReturn(true);
        when(views.get("7:views")).thenReturn(42L);

        assertThat(service.getViewCount(postId)).isEqualTo(42L);

        verify(postRepository, never()).findViewCountById(anyLong());
        verify(views, never()).setIfAbsent(anyString(), anyLong());
    }
}

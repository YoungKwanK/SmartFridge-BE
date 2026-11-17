package com.be16_2nd.SmartFridge.post.service;

import com.be16_2nd.SmartFridge.chat.domain.ChatParticipant;
import com.be16_2nd.SmartFridge.chat.domain.ChatRoom;
import com.be16_2nd.SmartFridge.chat.domain.PurchasePost;
import com.be16_2nd.SmartFridge.chat.repository.PurchasePostRepository;
import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.common.service.PostStatsService;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.post.domain.Post;
import com.be16_2nd.SmartFridge.post.domain.PostCategory;
import com.be16_2nd.SmartFridge.post.dto.PostPurchaseDto;
import com.be16_2nd.SmartFridge.post.dto.PostResDto;
import com.be16_2nd.SmartFridge.post.dto.PostSearchDto;
import com.be16_2nd.SmartFridge.post.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 게시판 목록에 공동구매 모집 현황(인원·상태·참여 여부)을 함께 담는지 확인하는 단위 테스트.
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class PostListPurchaseTest {

    @Mock FridgeAccessValidator fridgeAccessValidator;
    @Mock PostRepository postRepository;
    @Mock PostStatsService postStatsService;
    @Mock PurchasePostRepository purchasePostRepository;

    @InjectMocks PostService postService;

    final Long fridgeId = 1L;
    final Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
    final Member creator = Member.builder().id(UUID.randomUUID()).name("방장").email("creator@test.com").build();
    final Member viewer = Member.builder().id(UUID.randomUUID()).name("홍길동").email("viewer@test.com").build();
    final Pageable pageable = PageRequest.of(0, 10);

    @Test
    void 공동구매_글에는_모집_현황을_담는다() {
        loginAs(viewer);
        Post purchasePostItem = post(10L, PostCategory.공동구매);
        givenPosts(purchasePostItem);
        when(purchasePostRepository.findByPostIdIn(List.of(10L)))
                .thenReturn(List.of(purchase(purchasePostItem, 4, creator, viewer)));

        List<PostResDto> result = postService.findByPosts(fridgeId, pageable, new PostSearchDto(), "공동구매").getContent();

        PostPurchaseDto purchase = result.get(0).getPurchase();
        assertThat(purchase).isNotNull();
        assertThat(purchase.getCurrentParticipants()).isEqualTo(2);
        assertThat(purchase.getMaxParticipants()).isEqualTo(4);
        assertThat(purchase.getJoinStatus()).isEqualTo("OPEN");
        assertThat(purchase.getIsJoined()).isTrue();
        assertThat(purchase.getIsCreator()).isFalse();
    }

    @Test
    void 참여하지_않은_공동구매는_참여_여부가_거짓이다() {
        loginAs(viewer);
        Post purchasePostItem = post(10L, PostCategory.공동구매);
        givenPosts(purchasePostItem);
        when(purchasePostRepository.findByPostIdIn(List.of(10L)))
                .thenReturn(List.of(purchase(purchasePostItem, 3, creator)));

        PostPurchaseDto purchase = postService.findByPosts(fridgeId, pageable, new PostSearchDto(), "공동구매")
                .getContent().get(0).getPurchase();

        assertThat(purchase.getCurrentParticipants()).isEqualTo(1);
        assertThat(purchase.getIsJoined()).isFalse();
    }

    @Test
    void 공동구매가_아닌_글만_있으면_모집_정보를_찾지_않는다() {
        loginAs(viewer);
        givenPosts(post(20L, PostCategory.공지사항));

        List<PostResDto> result = postService.findByPosts(fridgeId, pageable, new PostSearchDto(), "공지사항").getContent();

        assertThat(result.get(0).getPurchase()).isNull();
        verify(purchasePostRepository, never()).findByPostIdIn(any());
    }

    @Test
    void 채팅방이_없는_공동구매_글은_모집_정보가_비어_있다() {
        loginAs(viewer);
        givenPosts(post(10L, PostCategory.공동구매));
        when(purchasePostRepository.findByPostIdIn(List.of(10L))).thenReturn(List.of());

        List<PostResDto> result = postService.findByPosts(fridgeId, pageable, new PostSearchDto(), "공동구매").getContent();

        assertThat(result.get(0).getPurchase()).isNull();
    }

    private void loginAs(Member who) {
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, who, Type.COMMON));
    }

    @SuppressWarnings("unchecked")
    private void givenPosts(Post... posts) {
        Page<Post> page = new PageImpl<>(List.of(posts), pageable, posts.length);
        when(postRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(postStatsService.getViewCount(anyLong())).thenReturn(0L);
    }

    private Post post(Long id, PostCategory category) {
        Post post = Post.builder().id(id).title("제목").content("내용").category(category)
                .member(creator).fridge(fridge).build();
        ReflectionTestUtils.setField(post, "createdTime", LocalDateTime.now());
        return post;
    }

    // 방장이 먼저 들어가 있고, others 가 뒤이어 참여한 공동구매 채팅방
    private PurchasePost purchase(Post post, int maxParticipants, Member roomCreator, Member... others) {
        ChatRoom room = ChatRoom.builder().id(100L).fridge(fridge).build();
        room.getParticipants().add(ChatParticipant.builder().chatRoom(room).member(roomCreator).build());
        for (Member other : others) {
            room.getParticipants().add(ChatParticipant.builder().chatRoom(room).member(other).build());
        }
        return PurchasePost.builder().chatRoom(room).post(post).creator(roomCreator)
                .title(post.getTitle()).maxParticipants(maxParticipants).build();
    }
}

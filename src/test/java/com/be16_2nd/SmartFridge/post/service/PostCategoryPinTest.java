package com.be16_2nd.SmartFridge.post.service;

import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.FridgeMember;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.fridge.repository.FridgeMemberRepository;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.notification.domain.NotificationType;
import com.be16_2nd.SmartFridge.notification.service.NotificationService;
import com.be16_2nd.SmartFridge.post.domain.Post;
import com.be16_2nd.SmartFridge.post.domain.PostCategory;
import com.be16_2nd.SmartFridge.post.dto.PostCreateDto;
import com.be16_2nd.SmartFridge.post.dto.PostUpdateDto;
import com.be16_2nd.SmartFridge.post.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 게시판 카테고리 enum(공지사항, 자유게시판, 공동구매)과 상단 고정 단위 테스트.
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인하므로 로컬 DB에 영향이 없다.
 */
@ExtendWith(MockitoExtension.class)
class PostCategoryPinTest {

    @Mock FridgeAccessValidator fridgeAccessValidator;
    @Mock PostRepository postRepository;
    @Mock NotificationService notificationService;
    @Mock FridgeMemberRepository fridgeMemberRepository;

    @InjectMocks PostService postService;

    final Long fridgeId = 1L;
    final Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
    final Member manager = Member.builder().id(UUID.randomUUID()).name("관리자").email("manager@test.com").build();
    final Member member = Member.builder().id(UUID.randomUUID()).name("홍길동").email("user@test.com").build();

    // ---------- 카테고리 ----------

    @Test
    void 카테고리_목록은_공지사항_자유게시판_공동구매다() {
        assertThat(postService.findAllCategories()).containsExactly("공지사항", "자유게시판", "공동구매");
    }

    @Test
    void 일반_멤버는_자유게시판과_공동구매_글을_쓸_수_있다() {
        loginAs(member, Type.COMMON);

        postService.createPost(fridgeId, createDto(PostCategory.자유게시판));
        postService.createPost(fridgeId, createDto(PostCategory.공동구매));

        ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
        verify(postRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Post::getCategory)
                .containsExactly(PostCategory.자유게시판, PostCategory.공동구매);
        assertThat(saved.getAllValues()).extracting(Post::isPinned).containsOnly(false);
    }

    @Test
    void 일반_멤버는_공지사항을_쓸_수_없다() {
        loginAs(member, Type.COMMON);

        assertThatThrownBy(() -> postService.createPost(fridgeId, createDto(PostCategory.공지사항)))
                .isInstanceOf(AccessDeniedException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void 카테고리가_없으면_글을_쓸_수_없다() {
        loginAs(member, Type.COMMON);

        assertThatThrownBy(() -> postService.createPost(fridgeId, createDto(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("카테고리를 선택해주세요.");

        verify(postRepository, never()).save(any());
    }

    @Test
    void 관리자가_공지사항을_쓰면_일반_멤버에게_공지_알림을_보낸다() {
        loginAs(manager, Type.MANAGER);
        when(fridgeMemberRepository.findAllByFridgeAndType(fridge, Type.COMMON))
                .thenReturn(List.of(FridgeMember.builder().fridge(fridge).member(member).type(Type.COMMON).build()));

        postService.createPost(fridgeId, createDto(PostCategory.공지사항));

        verify(notificationService).create(eq(manager), eq(member), eq(NotificationType.NEW_ANNOUNCEMENT), any(Post.class));
    }

    @Test
    void 일반_멤버는_수정으로도_공지사항으로_바꿀_수_없다() {
        loginAs(member, Type.COMMON);
        PostUpdateDto updateDto = PostUpdateDto.builder()
                .title("제목").content("내용").category(PostCategory.공지사항).build();

        assertThatThrownBy(() -> postService.updatePost(fridgeId, 10L, updateDto))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------- 상단 고정 ----------

    @Test
    void 관리자는_글을_고정하고_다시_누르면_해제한다() {
        loginAs(manager, Type.MANAGER);
        Post post = post(10L, fridge, PostCategory.공지사항);
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));

        assertThat(postService.togglePin(fridgeId, 10L)).isTrue();
        assertThat(post.isPinned()).isTrue();
        assertThat(post.getPinnedAt()).isNotNull();

        assertThat(postService.togglePin(fridgeId, 10L)).isFalse();
        assertThat(post.isPinned()).isFalse();
        assertThat(post.getPinnedAt()).isNull();
    }

    @Test
    void 공지사항이_아닌_글은_고정할_수_없다() {
        loginAs(manager, Type.MANAGER);
        Post post = post(10L, fridge, PostCategory.자유게시판);
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.togglePin(fridgeId, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("공지사항만 상단에 고정할 수 있습니다.");

        assertThat(post.isPinned()).isFalse();
    }

    @Test
    void 고정된_공지사항을_다른_카테고리로_바꾸면_고정이_풀린다() {
        Post post = post(10L, fridge, PostCategory.공지사항);
        post.togglePin();

        post.updatePost(PostUpdateDto.builder().title("제목").content("내용").category(PostCategory.자유게시판).build());

        assertThat(post.isPinned()).isFalse();
        assertThat(post.getPinnedAt()).isNull();
    }

    @Test
    void 고정된_공지사항은_내용만_수정해도_고정이_유지된다() {
        Post post = post(10L, fridge, PostCategory.공지사항);
        post.togglePin();

        post.updatePost(PostUpdateDto.builder().title("새 제목").content("새 내용").category(PostCategory.공지사항).build());

        assertThat(post.isPinned()).isTrue();
    }

    @Test
    void 일반_멤버는_글을_고정할_수_없다() {
        loginAs(member, Type.COMMON);

        assertThatThrownBy(() -> postService.togglePin(fridgeId, 10L))
                .isInstanceOf(AccessDeniedException.class);

        verify(postRepository, never()).findById(any());
    }

    @Test
    void 다른_냉장고의_글은_고정할_수_없다() {
        loginAs(manager, Type.MANAGER);
        Fridge otherFridge = Fridge.builder().id(2L).fridgeName("다른 냉장고").build();
        when(postRepository.findById(10L)).thenReturn(Optional.of(post(10L, otherFridge, PostCategory.공지사항)));

        assertThatThrownBy(() -> postService.togglePin(fridgeId, 10L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void loginAs(Member who, Type type) {
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, who, type));
    }

    private PostCreateDto createDto(PostCategory category) {
        return PostCreateDto.builder().title("제목").content("내용").category(category).build();
    }

    private Post post(Long id, Fridge postFridge, PostCategory category) {
        return Post.builder()
                .id(id)
                .title("제목")
                .content("내용")
                .category(category)
                .member(member)
                .fridge(postFridge)
                .build();
    }
}

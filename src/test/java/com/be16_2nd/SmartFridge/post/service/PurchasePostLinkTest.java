package com.be16_2nd.SmartFridge.post.service;

import com.be16_2nd.SmartFridge.chat.domain.PurchasePost;
import com.be16_2nd.SmartFridge.chat.repository.ChatRoomRepository;
import com.be16_2nd.SmartFridge.chat.repository.PurchasePostRepository;
import com.be16_2nd.SmartFridge.chat.service.ChatService;
import com.be16_2nd.SmartFridge.fridge.dto.FridgeCreateDto;
import com.be16_2nd.SmartFridge.fridge.repository.FridgeRepository;
import com.be16_2nd.SmartFridge.fridge.service.FridgeService;
import com.be16_2nd.SmartFridge.member.domain.SocialType;
import com.be16_2nd.SmartFridge.member.service.MemberService;
import com.be16_2nd.SmartFridge.post.domain.Post;
import com.be16_2nd.SmartFridge.post.domain.PostCategory;
import com.be16_2nd.SmartFridge.post.dto.PostCommentCreateDto;
import com.be16_2nd.SmartFridge.post.dto.PostCreateDto;
import com.be16_2nd.SmartFridge.post.dto.PostUpdateDto;
import com.be16_2nd.SmartFridge.post.repository.PostRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공동구매 글(purchase_post)과 게시판 글(post) 연결 통합 테스트 (로컬 DB, 테스트마다 롤백)
 * - 게시판에 공동구매 글을 쓰면 post + 채팅방이 같이 생긴다 (채팅에서 따로 방을 만드는 API 는 없다)
 * - 게시글을 지워도 채팅방은 남는다
 * - 방장이 나가 채팅방이 지워지면 게시글(댓글 포함)도 지워진다
 */
@SpringBootTest
@Transactional
class PurchasePostLinkTest {

    @Autowired MemberService memberService;
    @Autowired FridgeService fridgeService;
    @Autowired FridgeRepository fridgeRepository;
    @Autowired PostService postService;
    @Autowired PostCommentService postCommentService;
    @Autowired PostRepository postRepository;
    @Autowired ChatService chatService;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired PurchasePostRepository purchasePostRepository;
    @Autowired EntityManager em;

    Long fridgeId;

    @BeforeEach
    void setUp() {
        memberService.socialLogin(SocialType.GOOGLE, "pl-m", "manager@pl.com", true, "관리자", null);
        memberService.socialLogin(SocialType.GOOGLE, "pl-a", "a@pl.com", true, "회원A", null);
        memberService.socialLogin(SocialType.GOOGLE, "pl-b", "b@pl.com", true, "회원B", null);

        login("manager@pl.com");
        fridgeId = fridgeService.create(FridgeCreateDto.builder().fridgeName("공구냉장고").build()).getFridgeId();
        String inviteCode = fridgeRepository.findById(fridgeId).orElseThrow().getInviteCode();
        login("a@pl.com");
        fridgeService.joinFridge(inviteCode);
        login("b@pl.com");
        fridgeService.joinFridge(inviteCode);
        flushClear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 게시판에서_공동구매_글을_쓰면_채팅방이_생기고_작성자가_참여한다() {
        login("a@pl.com");
        Long postId = createPurchasePost("우유 공구", 4);
        flushClear();

        PurchasePost purchasePost = purchasePostRepository.findByPostId(postId).orElseThrow();
        assertThat(purchasePost.getTitle()).isEqualTo("우유 공구");
        assertThat(purchasePost.getMaxParticipants()).isEqualTo(4);
        assertThat(purchasePost.getCreator().getEmail()).isEqualTo("a@pl.com");
        assertThat(purchasePost.getChatRoom().getParticipants())
                .extracting(p -> p.getMember().getEmail()).containsExactly("a@pl.com");
    }

    @Test
    void 모집_인원이_없으면_공동구매_글을_쓸_수_없다() {
        login("a@pl.com");
        PostCreateDto createDto = PostCreateDto.builder()
                .title("인원 없음").content("내용").category(PostCategory.공동구매).build();

        assertThatThrownBy(() -> postService.createPost(fridgeId, createDto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 게시글을_삭제해도_채팅방은_남는다() {
        login("a@pl.com");
        Long postId = createPurchasePost("우유 공구", 4);
        Long roomId = purchasePostRepository.findByPostId(postId).orElseThrow().getChatRoom().getId();
        flushClear();

        postService.deletePost(fridgeId, postId);
        flushClear();

        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(chatRoomRepository.findById(roomId)).isPresent();
        PurchasePost purchasePost = purchasePostRepository.findByChatRoomId(roomId).orElseThrow();
        assertThat(purchasePost.getPost()).isNull();
        assertThat(purchasePost.getTitle()).isEqualTo("우유 공구");
        assertThat(chatService.getRoomInfo(roomId).getContents()).isNull();
    }

    @Test
    void 방장이_나가면_채팅방과_게시글_댓글이_같이_삭제된다() {
        login("a@pl.com");
        Long postId = createPurchasePost("우유 공구", 4);
        Long roomId = purchasePostRepository.findByPostId(postId).orElseThrow().getChatRoom().getId();
        login("b@pl.com");
        chatService.addParticipantToPurchaseChat(roomId);
        postCommentService.createPostComment(fridgeId, postId, new PostCommentCreateDto("저도 살게요"));
        flushClear();

        login("a@pl.com");
        chatService.leavePurchaseChatRoom(roomId);
        flushClear();

        assertThat(chatRoomRepository.findById(roomId)).isEmpty();
        assertThat(purchasePostRepository.findByChatRoomId(roomId)).isEmpty();
        assertThat(postRepository.findById(postId)).isEmpty();
        Long commentCount = em.createQuery("select count(c) from PostComment c where c.post.id = :postId", Long.class)
                .setParameter("postId", postId).getSingleResult();
        assertThat(commentCount).isZero();
    }

    @Test
    void 게시글_제목을_바꾸면_채팅방_이름도_바뀐다() {
        login("a@pl.com");
        Long postId = createPurchasePost("우유 공구", 4);
        flushClear();

        postService.updatePost(fridgeId, postId, PostUpdateDto.builder()
                .title("두유 공구").content("내용").category(PostCategory.공동구매).build());
        flushClear();

        assertThat(purchasePostRepository.findByPostId(postId).orElseThrow().getTitle()).isEqualTo("두유 공구");
    }

    @Test
    void 공동구매_글과_다른_카테고리는_서로_바꿀_수_없다() {
        login("a@pl.com");
        Long purchasePostId = createPurchasePost("우유 공구", 4);
        Long freePostId = postService.createPost(fridgeId, PostCreateDto.builder()
                .title("잡담").content("내용").category(PostCategory.자유게시판).build());
        flushClear();

        assertThatThrownBy(() -> postService.updatePost(fridgeId, purchasePostId, PostUpdateDto.builder()
                .title("우유 공구").content("내용").category(PostCategory.자유게시판).build()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> postService.updatePost(fridgeId, freePostId, PostUpdateDto.builder()
                .title("잡담").content("내용").category(PostCategory.공동구매).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 냉장고를_삭제해도_공동구매_글과_채팅방이_함께_정리된다() {
        login("a@pl.com");
        Long postId = createPurchasePost("우유 공구", 4);
        Long roomId = purchasePostRepository.findByPostId(postId).orElseThrow().getChatRoom().getId();
        flushClear();

        login("manager@pl.com");
        fridgeService.deleteFridge(fridgeId);
        flushClear();

        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(chatRoomRepository.findById(roomId)).isEmpty();
    }

    private Long createPurchasePost(String title, int maxParticipants) {
        return postService.createPost(fridgeId, PostCreateDto.builder()
                .title(title).content("같이 사요").category(PostCategory.공동구매)
                .maxParticipants(maxParticipants).productUrl("https://shop.example.com/milk").build());
    }

    private void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private void flushClear() {
        em.flush();
        em.clear();
    }
}

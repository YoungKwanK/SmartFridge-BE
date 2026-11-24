package com.be16_2nd.SmartFridge.post.service;

import com.be16_2nd.SmartFridge.member.repository.MemberRepository;

import java.util.UUID;

import com.be16_2nd.SmartFridge.fridge.dto.FridgeCreateDto;
import com.be16_2nd.SmartFridge.fridge.service.FridgeService;
import com.be16_2nd.SmartFridge.member.domain.SocialType;
import com.be16_2nd.SmartFridge.member.service.MemberService;
import com.be16_2nd.SmartFridge.post.domain.PostCategory;
import com.be16_2nd.SmartFridge.post.dto.PostCreateDto;
import com.be16_2nd.SmartFridge.post.dto.PostResDto;
import com.be16_2nd.SmartFridge.post.dto.PostSearchDto;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 게시판 목록: 대표 이미지(첫 첨부 이미지)와 공동구매 모집 인원이 정확하고,
 * 글·이미지·공동구매 글이 늘어도 쿼리 수가 같은지 확인 (N+1 회귀 방지)
 */
@SpringBootTest
@Transactional
class PostListQueryCountTest {

    @Autowired MemberService memberService;
    @Autowired MemberRepository memberRepository;
    @Autowired FridgeService fridgeService;
    @Autowired PostService postService;
    @Autowired EntityManager em;

    Statistics statistics;
    Long fridgeId;
    int seq;

    @BeforeEach
    void setUp() {
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        memberService.socialLogin(SocialType.GOOGLE, "pl-m", "writer@pl.com", true, "작성자", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(idOf("writer@pl.com").toString(), null, List.of()));
        fridgeId = fridgeService.create(FridgeCreateDto.builder().fridgeName("목록냉장고").build()).getFridgeId();
    }

    @AfterEach
    void tearDown() {
        statistics.setStatisticsEnabled(false);
        SecurityContextHolder.clearContext();
    }

    @Test
    void 대표_이미지는_첫_첨부_이미지이고_공동구매_글만_모집_인원이_있다() {
        Long purchaseId = write(PostCategory.공동구매, List.of("https://img/a1.jpg", "https://img/a2.jpg"));
        Long freeId = write(PostCategory.자유게시판, List.of("https://img/b1.jpg"));
        Long noImageId = write(PostCategory.자유게시판, null);
        flushClear();

        Map<Long, PostResDto> byId = list().stream().collect(Collectors.toMap(PostResDto::getId, p -> p));

        assertThat(byId.get(purchaseId).getThumbnailUrl()).isEqualTo("https://img/a1.jpg");
        assertThat(byId.get(purchaseId).getPurchase().getCurrentParticipants()).isEqualTo(1);
        assertThat(byId.get(purchaseId).getPurchase().getMaxParticipants()).isEqualTo(4);
        assertThat(byId.get(freeId).getThumbnailUrl()).isEqualTo("https://img/b1.jpg");
        assertThat(byId.get(freeId).getPurchase()).isNull();
        assertThat(byId.get(noImageId).getThumbnailUrl()).isNull();
    }

    @Test
    void 글과_이미지가_늘어도_목록_쿼리_수가_같다() {
        writeMixed(2);
        long small = countQueries();

        writeMixed(6);
        long large = countQueries();

        assertThat(large).isEqualTo(small);
    }

    // 공동구매 글(이미지 2장)과 자유게시판 글(이미지 1장)을 count 개씩
    private void writeMixed(int count) {
        for (int i = 0; i < count; i++) {
            write(PostCategory.공동구매, List.of("https://img/p" + seq + "-1.jpg", "https://img/p" + seq + "-2.jpg"));
            write(PostCategory.자유게시판, List.of("https://img/f" + seq + ".jpg"));
        }
    }

    private Long write(PostCategory category, List<String> imageUrls) {
        seq++;
        return postService.createPost(fridgeId, PostCreateDto.builder()
                .title("글" + seq).content("내용").category(category).imageUrls(imageUrls)
                .maxParticipants(category == PostCategory.공동구매 ? 4 : null).build());
    }

    private List<PostResDto> list() {
        return postService.findByPosts(fridgeId, PageRequest.of(0, 20), new PostSearchDto(), "전체").getContent();
    }

    private long countQueries() {
        flushClear();
        statistics.clear();
        list();
        return statistics.getPrepareStatementCount();
    }

    private void flushClear() {
        em.flush();
        em.clear();
    }

    // 테스트 회원의 id (principal 이름·채팅 식별자는 회원 id)
    private UUID idOf(String email) {
        return memberRepository.findByEmail(email).orElseThrow().getId();
    }
}

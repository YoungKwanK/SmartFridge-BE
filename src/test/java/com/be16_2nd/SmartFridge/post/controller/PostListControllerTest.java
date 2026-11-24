package com.be16_2nd.SmartFridge.post.controller;

import com.be16_2nd.SmartFridge.post.dto.PostPurchaseDto;
import com.be16_2nd.SmartFridge.post.dto.PostResDto;
import com.be16_2nd.SmartFridge.post.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게시판 목록 API 가 대표 이미지와 공동구매 모집 인원을 JSON 으로 내려주는지 확인 (서비스는 가짜 객체)
 */
@ExtendWith(MockitoExtension.class)
class PostListControllerTest {

    @Mock PostService postService;
    @InjectMocks PostController postController;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(postController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void 목록에_대표_이미지와_모집_인원을_내려준다() throws Exception {
        PostResDto purchasePost = PostResDto.builder().id(10L).title("휴지 공구").categoryName("공동구매")
                .thumbnailUrl("https://img/first.jpg")
                .purchase(PostPurchaseDto.builder().currentParticipants(2).maxParticipants(4).build())
                .build();
        PostResDto freePost = PostResDto.builder().id(20L).title("안녕하세요").categoryName("자유게시판").build();
        when(postService.findByPosts(eq(1L), any(), any(), eq("전체")))
                .thenReturn(new PageImpl<>(List.of(purchasePost, freePost), PageRequest.of(0, 10), 2));

        mockMvc.perform(get("/fridge/1/post/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].thumbnailUrl").value("https://img/first.jpg"))
                .andExpect(jsonPath("$.result.content[0].purchase.currentParticipants").value(2))
                .andExpect(jsonPath("$.result.content[0].purchase.maxParticipants").value(4))
                .andExpect(jsonPath("$.result.content[1].thumbnailUrl").value(nullValue()))
                .andExpect(jsonPath("$.result.content[1].purchase").value(nullValue()));
    }
}

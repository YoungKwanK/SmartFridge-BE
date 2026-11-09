package com.be16_2nd.SmartFridge.inquiry.controller;

import com.be16_2nd.SmartFridge.common.service.CommonExceptionHandler;
import com.be16_2nd.SmartFridge.inquiry.service.InquiryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.HttpMethod;

/**
 * 문의 등록·수정 요청의 입력 검증 테스트. 서비스는 가짜 객체라 DB와 S3를 사용하지 않는다.
 */
class InquiryRequestValidationTest {

    private final InquiryService inquiryService = mock(InquiryService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InquiryController(inquiryService))
                .setControllerAdvice(new CommonExceptionHandler())
                .build();
    }

    @Test
    void 필수값을_모두_보내면_문의가_등록된다() throws Exception {
        mockMvc.perform(multipart("/inquiry/create")
                        .param("inquiryType", "BUG")
                        .param("title", "제목")
                        .param("contents", "내용"))
                .andExpect(status().isCreated());
    }

    @Test
    void 제목이_공백이면_등록할_수_없다() throws Exception {
        mockMvc.perform(multipart("/inquiry/create")
                        .param("inquiryType", "BUG")
                        .param("title", "   ")
                        .param("contents", "내용"))
                .andExpect(status().isBadRequest());

        verify(inquiryService, never()).create(any());
    }

    @Test
    void 문의_유형이_없으면_등록할_수_없다() throws Exception {
        mockMvc.perform(multipart("/inquiry/create")
                        .param("title", "제목")
                        .param("contents", "내용"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 내용이_5000자를_넘으면_등록할_수_없다() throws Exception {
        mockMvc.perform(multipart("/inquiry/create")
                        .param("inquiryType", "BUG")
                        .param("title", "제목")
                        .param("contents", "가".repeat(5001)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 수정할_때_보내지_않은_값은_검증하지_않는다() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PATCH, "/inquiry/update/1")
                        .param("title", "제목만 수정"))
                .andExpect(status().isOk());
    }

    @Test
    void 수정할_때_내용을_공백으로_바꿀_수_없다() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PATCH, "/inquiry/update/1")
                        .param("contents", "   "))
                .andExpect(status().isBadRequest());

        verify(inquiryService, never()).updateInquiry(eq(1L), any());
    }
}

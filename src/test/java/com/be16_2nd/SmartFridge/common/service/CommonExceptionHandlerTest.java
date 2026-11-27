package com.be16_2nd.SmartFridge.common.service;

import com.be16_2nd.SmartFridge.member.domain.SocialType;
import com.be16_2nd.SmartFridge.member.exception.SocialAccountConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 처리하지 못한 예외가 응답에 내부 정보를 흘리지 않는지, 스프링 요청 오류가 500으로 바뀌지 않는지 확인한다.
 */
class CommonExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class FailingController {
        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("could not execute statement; SQL [insert into member ...]; constraint [uk_email]");
        }

        @GetMapping("/npe")
        String npe() {
            String value = null;
            return value.trim();
        }

        @GetMapping("/social-conflict")
        String socialConflict() {
            throw new SocialAccountConflictException(SocialType.KAKAO);
        }

        @GetMapping("/required")
        String required(@RequestParam String name) {
            return name;
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new CommonExceptionHandler())
                .build();
    }

    @Test
    void 처리하지_못한_예외는_내부_메시지_대신_고정_문구로_응답한다() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status_code").value(500))
                .andExpect(jsonPath("$.status_message").value(CommonExceptionHandler.INTERNAL_ERROR_MESSAGE))
                .andExpect(content().string(not(containsString("SQL"))));
    }

    @Test
    void 메시지가_없는_예외도_고정_문구로_응답한다() throws Exception {
        mockMvc.perform(get("/npe"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status_message").value(CommonExceptionHandler.INTERNAL_ERROR_MESSAGE));
    }

    @Test
    void 필수_파라미터가_없으면_400으로_응답한다() throws Exception {
        mockMvc.perform(get("/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status_code").value(400));
    }

    @Test
    void 지원하지_않는_메서드면_405로_응답한다() throws Exception {
        mockMvc.perform(post("/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status_code").value(405));
    }

    @Test
    void 다른_소셜계정으로_가입된_이메일이면_409와_기존_제공자를_응답한다() throws Exception {
        mockMvc.perform(get("/social-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status_code").value(409))
                .andExpect(jsonPath("$.status_message").value(containsString("카카오")))
                .andExpect(jsonPath("$.result.code").value(SocialAccountConflictException.CODE))
                .andExpect(jsonPath("$.result.provider").value("KAKAO"))
                .andExpect(jsonPath("$.result.providerName").value("카카오"));
    }
}

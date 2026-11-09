package com.be16_2nd.SmartFridge.inquiry.service;

import com.be16_2nd.SmartFridge.common.service.CommonExceptionHandler;
import com.be16_2nd.SmartFridge.common.service.S3Uploader;
import com.be16_2nd.SmartFridge.inquiry.domain.Inquiry;
import com.be16_2nd.SmartFridge.inquiry.domain.InquiryStatus;
import com.be16_2nd.SmartFridge.inquiry.dto.InquiryUpdateDto;
import com.be16_2nd.SmartFridge.inquiry.exception.InquiryConflictException;
import com.be16_2nd.SmartFridge.inquiry.repository.InquiryRepository;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.member.repository.MemberRepository;
import com.be16_2nd.SmartFridge.notification.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 문의 수정·삭제 소유자 검증과 권한 오류 응답 단위 테스트.
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인하므로 로컬 DB에 영향이 없다.
 */
@ExtendWith(MockitoExtension.class)
class InquiryOwnerCheckTest {

    @Mock InquiryRepository inquiryRepository;
    @Mock MemberRepository memberRepository;
    @Mock NotificationService notificationService;
    @Mock S3Uploader s3Uploader;

    @InjectMocks InquiryService inquiryService;

    final Long inquiryId = 1L;
    final Member owner = Member.builder().id(UUID.randomUUID()).name("작성자").email("owner@test.com").build();
    final Member other = Member.builder().id(UUID.randomUUID()).name("다른 사람").email("other@test.com").build();

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 작성자는_답변_대기_중인_문의를_수정할_수_있다() {
        Inquiry inquiry = givenInquiry(InquiryStatus.PENDING);
        loginAs(owner);

        inquiryService.updateInquiry(inquiryId, updateDto("수정된 제목"));

        assertThat(inquiry.getTitle()).isEqualTo("수정된 제목");
    }

    @Test
    void 다른_사람의_문의는_수정할_수_없다() {
        Inquiry inquiry = givenInquiry(InquiryStatus.PENDING);
        loginAs(other);

        assertThatThrownBy(() -> inquiryService.updateInquiry(inquiryId, updateDto("가로챈 제목")))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(inquiry.getTitle()).isEqualTo("원래 제목");
    }

    @Test
    void 답변이_완료된_문의는_작성자도_수정할_수_없다() {
        givenInquiry(InquiryStatus.COMPLETED);
        loginAs(owner);

        assertThatThrownBy(() -> inquiryService.updateInquiry(inquiryId, updateDto("수정된 제목")))
                .isInstanceOf(InquiryConflictException.class);
    }

    @Test
    void 취소된_문의는_작성자도_수정할_수_없다() {
        givenInquiry(InquiryStatus.CANCELLED);
        loginAs(owner);

        assertThatThrownBy(() -> inquiryService.updateInquiry(inquiryId, updateDto("수정된 제목")))
                .isInstanceOf(InquiryConflictException.class);
    }

    @Test
    void 없는_문의를_수정하면_찾을_수_없음_오류가_난다() {
        when(inquiryRepository.findById(inquiryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.updateInquiry(inquiryId, updateDto("수정된 제목")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void 작성자는_문의를_삭제할_수_있다() {
        Inquiry inquiry = givenInquiry(InquiryStatus.COMPLETED);
        loginAs(owner);

        inquiryService.deleteInquiry(inquiryId);

        verify(inquiryRepository).delete(inquiry);
    }

    @Test
    void 다른_사람의_문의는_삭제할_수_없다() {
        givenInquiry(InquiryStatus.PENDING);
        loginAs(other);

        assertThatThrownBy(() -> inquiryService.deleteInquiry(inquiryId))
                .isInstanceOf(AccessDeniedException.class);

        verify(inquiryRepository, never()).delete(any(Inquiry.class));
    }

    @Test
    void 권한_오류는_403으로_응답한다() {
        var response = new CommonExceptionHandler().accessDeniedException(new AccessDeniedException("권한 없음"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private Inquiry givenInquiry(InquiryStatus status) {
        Inquiry inquiry = Inquiry.builder()
                .inquiryId(inquiryId)
                .title("원래 제목")
                .member(owner)
                .status(status)
                .build();
        when(inquiryRepository.findById(inquiryId)).thenReturn(Optional.of(inquiry));
        return inquiry;
    }

    private void loginAs(Member member) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member.getEmail(), null, List.of()));
        when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
    }

    private InquiryUpdateDto updateDto(String title) {
        InquiryUpdateDto dto = new InquiryUpdateDto();
        dto.setTitle(title);
        return dto;
    }
}

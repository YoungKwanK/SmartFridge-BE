package com.be16_2nd.SmartFridge.inquiry.service;

import com.be16_2nd.SmartFridge.inquiry.domain.Inquiry;
import com.be16_2nd.SmartFridge.inquiry.domain.InquiryStatus;
import com.be16_2nd.SmartFridge.inquiry.exception.InquiryConflictException;
import com.be16_2nd.SmartFridge.inquiry.repository.InquiryRepository;
import com.be16_2nd.SmartFridge.inquiryComment.domain.InquiryComment;
import com.be16_2nd.SmartFridge.inquiryComment.dto.InquiryCommentCreateDto;
import com.be16_2nd.SmartFridge.inquiryComment.dto.InquiryCommentUpdateDto;
import com.be16_2nd.SmartFridge.inquiryComment.repository.InquiryCommentRepository;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.member.service.AdminAccountProvider;
import com.be16_2nd.SmartFridge.notification.domain.NotificationType;
import com.be16_2nd.SmartFridge.notification.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * AdminService에서 분리한 문의 관리 기능 단위 테스트.
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class InquiryAdminServiceTest {

    @Mock InquiryRepository inquiryRepository;
    @Mock InquiryCommentRepository inquiryCommentRepository;
    @Mock NotificationService notificationService;
    @Mock AdminAccountProvider adminAccountProvider;

    @InjectMocks InquiryAdminService inquiryAdminService;

    final Long inquiryId = 1L;
    final Member admin = Member.builder().id(UUID.randomUUID()).name("관리자").email("admin@test.com").build();
    final Member writer = Member.builder().id(UUID.randomUUID()).name("작성자").email("writer@test.com").build();

    @Test
    void 답변을_등록하면_완료_상태가_되고_작성자에게_알림을_보낸다() {
        Inquiry inquiry = inquiry(InquiryStatus.PENDING);
        when(inquiryRepository.findByIdForUpdate(inquiryId)).thenReturn(Optional.of(inquiry));
        when(adminAccountProvider.getAdmin()).thenReturn(admin);
        when(inquiryCommentRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        inquiryAdminService.replyInquiry(new InquiryCommentCreateDto("답변입니다"), inquiryId);

        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.COMPLETED);
        assertThat(inquiry.getComments().getCommentContents()).isEqualTo("답변입니다");
        verify(notificationService).create(eq(admin), eq(writer), eq(NotificationType.ADMIN_REPLY), any());
    }

    @Test
    void 이미_답변한_문의에는_다시_답변할_수_없다() {
        Inquiry inquiry = inquiry(InquiryStatus.COMPLETED);
        inquiry.completeWithReply(InquiryComment.builder().commentContents("기존 답변").inquiry(inquiry).build());
        when(inquiryRepository.findByIdForUpdate(inquiryId)).thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> inquiryAdminService.replyInquiry(new InquiryCommentCreateDto("답변"), inquiryId))
                .isInstanceOf(InquiryConflictException.class);

        verify(inquiryCommentRepository, never()).saveAndFlush(any());
    }

    @Test
    void 답변이_없는_문의의_답변을_수정하면_찾을_수_없음_오류가_난다() {
        when(inquiryRepository.findAllByInquiryId(inquiryId)).thenReturn(Optional.of(inquiry(InquiryStatus.PENDING)));

        assertThatThrownBy(() -> inquiryAdminService.updateReply(inquiryId, new InquiryCommentUpdateDto()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void 답변_내용을_수정하면_작성자에게_알림을_보낸다() {
        Inquiry inquiry = answeredInquiry("기존 답변");
        when(inquiryRepository.findAllByInquiryId(inquiryId)).thenReturn(Optional.of(inquiry));

        inquiryAdminService.updateReply(inquiryId, new InquiryCommentUpdateDto("수정한 답변"));

        assertThat(inquiry.getComments().getCommentContents()).isEqualTo("수정한 답변");
        verify(notificationService).create(eq(writer), eq(NotificationType.ADMIN_REPLY_UPDATED), any());
    }

    @Test
    void 답변_내용이_같으면_알림을_보내지_않는다() {
        Inquiry inquiry = answeredInquiry("기존 답변");
        when(inquiryRepository.findAllByInquiryId(inquiryId)).thenReturn(Optional.of(inquiry));

        inquiryAdminService.updateReply(inquiryId, new InquiryCommentUpdateDto("기존 답변"));

        verify(notificationService, never()).create(any(), any(), any());
    }

    private Inquiry answeredInquiry(String reply) {
        Inquiry inquiry = inquiry(InquiryStatus.PENDING);
        inquiry.completeWithReply(InquiryComment.builder().commentContents(reply).inquiry(inquiry).build());
        return inquiry;
    }

    @Test
    void 관리자는_문의_상태를_변경할_수_있다() {
        Inquiry inquiry = inquiry(InquiryStatus.PENDING);
        when(inquiryRepository.findAllByInquiryId(inquiryId)).thenReturn(Optional.of(inquiry));

        inquiryAdminService.updateInquiry(inquiryId, InquiryStatus.IN_PROGRESS);

        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.IN_PROGRESS);
    }

    @Test
    void 문의_내용_수정은_보낸_값만_바꾼다() {
        Inquiry inquiry = inquiry(InquiryStatus.PENDING);

        inquiry.updateContents(null, "새 제목", null);

        assertThat(inquiry.getTitle()).isEqualTo("새 제목");
        assertThat(inquiry.getContents()).isEqualTo("원래 내용");
    }

    @Test
    void 답변이_연결된_문의도_toString이_재귀하지_않는다() {
        Inquiry inquiry = inquiry(InquiryStatus.PENDING);
        inquiry.completeWithReply(InquiryComment.builder().commentContents("답변").inquiry(inquiry).build());

        assertThat(inquiry.toString()).isNotBlank();
    }

    private Inquiry inquiry(InquiryStatus status) {
        return Inquiry.builder()
                .inquiryId(inquiryId)
                .title("원래 제목")
                .contents("원래 내용")
                .member(writer)
                .status(status)
                .build();
    }
}

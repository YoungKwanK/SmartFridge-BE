package com.be16_2nd.SmartFridge.inquiry.service;

import com.be16_2nd.SmartFridge.inquiry.domain.Inquiry;
import com.be16_2nd.SmartFridge.inquiry.domain.InquiryStatus;
import com.be16_2nd.SmartFridge.inquiry.dto.InquiryResDto;
import com.be16_2nd.SmartFridge.inquiry.exception.InquiryConflictException;
import com.be16_2nd.SmartFridge.inquiry.repository.InquiryRepository;
import com.be16_2nd.SmartFridge.inquiryComment.domain.InquiryComment;
import com.be16_2nd.SmartFridge.inquiryComment.dto.InquiryCommentCreateDto;
import com.be16_2nd.SmartFridge.inquiryComment.dto.InquiryCommentResDto;
import com.be16_2nd.SmartFridge.inquiryComment.dto.InquiryCommentUpdateDto;
import com.be16_2nd.SmartFridge.inquiryComment.repository.InquiryCommentRepository;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.member.service.AdminAccountProvider;
import com.be16_2nd.SmartFridge.notification.domain.NotificationType;
import com.be16_2nd.SmartFridge.notification.repository.NotificationRepository;
import com.be16_2nd.SmartFridge.notification.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class InquiryAdminService {

    private final InquiryRepository inquiryRepository;
    private final InquiryCommentRepository inquiryCommentRepository;
    private final NotificationService notificationService;
    private final AdminAccountProvider adminAccountProvider;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<InquiryResDto> findAllInquiry() {
        return inquiryRepository.findAll().stream()
                .map(InquiryResDto::fromEntity)
                .collect(Collectors.toList());
    }

    public InquiryCommentResDto replyInquiry(InquiryCommentCreateDto inquiryCommentCreateDto, Long inquiryId) {
        Inquiry inquiry = inquiryRepository.findByIdForUpdate(inquiryId)
                .orElseThrow(() -> new EntityNotFoundException("등록되지 않은 문의입니다."));
        inquiry.validateReplyable();
        Member sender = adminAccountProvider.getAdmin();
        Member receiver = inquiry.getMember();
        InquiryComment inquiryComment;
        try {
            inquiryComment = inquiryCommentRepository.saveAndFlush(inquiryCommentCreateDto.toEntity(inquiryCommentCreateDto, inquiry));
        } catch (DataIntegrityViolationException e) {
            throw new InquiryConflictException("이미 답변이 등록된 문의입니다.");
        }
        inquiry.completeWithReply(inquiryComment);
        // 알림 발송 + db 저장
        notificationService.create(sender, receiver, NotificationType.ADMIN_REPLY, inquiryComment);
        // 답변으로 처리가 끝났으므로 관리자의 새 문의 알림은 읽음 처리
        notificationRepository.markAsReadByTarget(sender, NotificationType.NEW_INQUIRY, inquiryId);
        return InquiryCommentResDto.builder()
                .id(inquiryComment.getCommentId())
                .contents(inquiryComment.getCommentContents())
                .inquiryId(inquiryId)
                .createdTime(inquiryComment.getCreatedTime())
                .build();
    }

    public InquiryResDto inquiryDetail(Long inquiryId) {
        return InquiryResDto.fromEntity(getInquiry(inquiryId));
    }

    public InquiryResDto updateInquiry(Long inquiryId, InquiryStatus status) {
        Inquiry inquiry = getInquiry(inquiryId);
        inquiry.changeStatus(status);
        // 답변완료·반려로 문의가 끝나면 관리자의 새 문의 알림은 읽음 처리
        if (status == InquiryStatus.COMPLETED || status == InquiryStatus.CANCELLED) {
            notificationRepository.markAsReadByTarget(adminAccountProvider.getAdmin(), NotificationType.NEW_INQUIRY, inquiryId);
        }
        return InquiryResDto.fromEntity(inquiry);
    }

    public InquiryResDto updateReply(Long inquiryId, InquiryCommentUpdateDto dto) {
        Inquiry inquiry = getInquiry(inquiryId);
        InquiryComment inquiryComment = inquiry.getComments();
        if (inquiryComment == null) {
            throw new EntityNotFoundException("답변이 없습니다.");
        }
        boolean changed = !Objects.equals(inquiryComment.getCommentContents(), dto.getNewCommentContents());
        inquiryComment.updateCommentContents(dto.getNewCommentContents());
        // 내용이 실제로 바뀐 경우에만 문의 작성자에게 알림
        if (changed) {
            notificationService.create(inquiry.getMember(), NotificationType.ADMIN_REPLY_UPDATED, inquiryComment);
        }
        return InquiryResDto.fromEntity(inquiry);
    }

    private Inquiry getInquiry(Long inquiryId) {
        return inquiryRepository.findAllByInquiryId(inquiryId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 문의글입니다."));
    }
}

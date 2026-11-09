package com.be16_2nd.SmartFridge.inquiry.service;

import com.be16_2nd.SmartFridge.common.service.S3Uploader;
import com.be16_2nd.SmartFridge.inquiry.domain.InquiryImage;
import com.be16_2nd.SmartFridge.inquiry.dto.InquiryCreateDto;
import com.be16_2nd.SmartFridge.inquiry.domain.Inquiry;
import com.be16_2nd.SmartFridge.inquiry.dto.InquiryResDto;
import com.be16_2nd.SmartFridge.inquiry.dto.InquiryUpdateDto;
import com.be16_2nd.SmartFridge.inquiry.repository.InquiryRepository;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.member.repository.MemberRepository;
import com.be16_2nd.SmartFridge.member.service.MemberService;
import com.be16_2nd.SmartFridge.notification.domain.NotificationType;
import com.be16_2nd.SmartFridge.notification.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;
    private final S3Uploader s3Uploader;

    public Long create(InquiryCreateDto inquiryCreateDto) {

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member sender = memberRepository.findByEmail(email)
                .orElseThrow(()-> new EntityNotFoundException("등록되지 않은 사용자입니다."));
        Member receiver = memberRepository.findByEmail("admin@naver.com")
                .orElseThrow(() -> new EntityNotFoundException("등록되지 않은 관리자입니다."));

        // 업로드 전에 전부 검증 (일부만 올라가는 것을 막는다)
        List<MultipartFile> images = InquiryImageValidator.validate(inquiryCreateDto.getImageFiles(), 0);

        Inquiry inquiry = inquiryCreateDto.toEntity(sender);
        addImages(inquiry, images);
        inquiryRepository.save(inquiry);
        // 알림 발송 + db 저장
        notificationService.create(sender, receiver, NotificationType.NEW_INQUIRY, inquiry);

        return inquiry.getInquiryId();
    }


    @Transactional(readOnly = true)
    public List<InquiryResDto> findMyInquiry(){
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(()->new EntityNotFoundException("존재하지 않는 사용자 입니다"));
        return inquiryRepository.findAllByMember(member).stream()
                .map(i-> InquiryResDto.fromEntity(i)).collect(Collectors.toList());
    }

    public Long updateInquiry(Long inquiryId, InquiryUpdateDto inquiryUpdateDto){
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 글입니다."));
        validateOwner(inquiry, "본인 문의만 수정할 수 있습니다.");
        inquiry.validateEditable();

        // 값을 바꾸거나 S3를 건드리기 전에 이미지 요청부터 모두 검증
        List<InquiryImage> removedImages = inquiry.findImagesToRemove(inquiryUpdateDto.parseRemovedImageIndexes());
        int keptImageCount = inquiry.getInquiryImages().size() - removedImages.size();
        List<MultipartFile> newImages = InquiryImageValidator.validate(inquiryUpdateDto.getImageFiles(), keptImageCount);

        if (inquiryUpdateDto.getTitle() != null)
            inquiry.setTitle(inquiryUpdateDto.getTitle());
        if (inquiryUpdateDto.getContents() != null)
            inquiry.setContents(inquiryUpdateDto.getContents());
        if (inquiryUpdateDto.getInquiryType() != null)
            inquiry.setInquiryType(inquiryUpdateDto.getInquiryType());

        // 업로드가 실패해도 기존 이미지가 지워지지 않도록 새 이미지부터 올린다
        addImages(inquiry, newImages);
        for (InquiryImage removedImage : removedImages) {
            s3Uploader.delete(removedImage.getImageUrl());
        }
        inquiry.getInquiryImages().removeAll(removedImages);

        return inquiry.getInquiryId();
    }

    private void addImages(Inquiry inquiry, List<MultipartFile> images) {
        for (MultipartFile image : images) {
            String url = s3Uploader.upload(image);
            InquiryImage inquiryImage = InquiryImage.builder()
                    .imageUrl(url)
                    .inquiry(inquiry)
                    .build();
            inquiry.getInquiryImages().add(inquiryImage);
        }
    }

    public Long deleteInquiry(Long inquiryId){
        Inquiry inquiry = inquiryRepository.findById(inquiryId).orElseThrow(()->new EntityNotFoundException("존재하지 않는 글입니다."));
        validateOwner(inquiry, "본인 문의만 삭제할 수 있습니다.");
        List<String> imageUrls = inquiry.getInquiryImages().stream()
                .map(InquiryImage::getImageUrl)
                .toList();
        inquiryRepository.delete(inquiry);
        imageUrls.forEach(s3Uploader::delete);
    return inquiryId;
    }

    private void validateOwner(Inquiry inquiry, String message) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(()->new EntityNotFoundException("존재하지 않는 사용자입니다."));
        if (!inquiry.isOwner(member)) {
            throw new AccessDeniedException(message);
        }
    }
}

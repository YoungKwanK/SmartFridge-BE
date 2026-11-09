package com.be16_2nd.SmartFridge.member.service;

import com.be16_2nd.SmartFridge.food.repository.FoodRepository;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.inquiry.domain.InquiryStatus;
import com.be16_2nd.SmartFridge.inquiry.domain.InquiryType;
import com.be16_2nd.SmartFridge.inquiry.repository.InquiryRepository;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.member.dto.MemberResDto;
import com.be16_2nd.SmartFridge.member.dto.AdminDashbordResDto;
import com.be16_2nd.SmartFridge.member.repository.MemberRepository;
import com.be16_2nd.SmartFridge.notification.domain.Notification;
import com.be16_2nd.SmartFridge.notification.domain.NotificationType;
import com.be16_2nd.SmartFridge.notification.dto.NotificationResDto;
import com.be16_2nd.SmartFridge.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class AdminService {

    private final MemberRepository memberRepository;
    private final InquiryRepository inquiryRepository;
    private final NotificationRepository notificationRepository;
    private final FoodRepository foodRepository;
    private final AdminAccountProvider adminAccountProvider;


    @Transactional(readOnly = true)
    public List<MemberResDto> findAllMember(){
        return memberRepository.findAll().stream()
                .map(m->MemberResDto.fromEntity(m)).collect(Collectors.toList());
    }

    public Member deleteMember(String memberEmail){
        Member member = memberRepository.findByEmail(memberEmail)
                .orElseThrow(()->new EntityNotFoundException("Member Not Found"));
        // 회원 탈퇴와 같은 규칙: 식품은 등록자 없음 + 공유 식품으로 남김 (회원 삭제 cascade로 지워지지 않게 먼저 처리)
        foodRepository.releaseAllByMember(member);
        memberRepository.delete(member);
        return member;
    }

    public AdminDashbordResDto dashbord(){
        Member member = adminAccountProvider.getAdmin();

        return AdminDashbordResDto.builder()
                .inquiryCount(inquiryRepository.count())
                .unansweredCount(inquiryRepository.countAllByStatus(InquiryStatus.PENDING))
                .userCount(memberRepository.count())
                .newNotification(notificationRepository.countByReceiverAndIsReadFalse(member))
                .build();
    }

    public MemberResDto memberDetail(String memberEmail){
        Member member = memberRepository.findByEmail(memberEmail)
                .orElseThrow(()-> new EntityNotFoundException("존재하지 않는 회원입니다"));
        return MemberResDto.fromEntity(member);
    }

    public List<NotificationResDto> findAllNotification(){
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(()->new EntityNotFoundException("ONLY ADMIN ALLOWED"));
        return toAdminNotificationDtos(notificationRepository.findByReceiverOrderByIsReadAscCreatedTimeDesc(member));
    }
    public List<NotificationResDto> findAllReadNotification(){
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(()->new EntityNotFoundException("ONLY ADMIN ALLOWED"));
        return toAdminNotificationDtos(notificationRepository.findByReceiverAndIsReadTrueOrderByCreatedTimeDesc(member));
    }
    public List<NotificationResDto> findAllUnreadNotification(){
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(()->new EntityNotFoundException("ONLY ADMIN ALLOWED"));
        return toAdminNotificationDtos(notificationRepository.findByReceiverAndIsReadFalseOrderByCreatedTimeDesc(member));
    }

    // 새 문의 알림에는 사용자가 선택한 문의 유형을 함께 내려준다 (알림 수와 상관없이 문의 조회 1번)
    private List<NotificationResDto> toAdminNotificationDtos(List<Notification> notifications) {
        List<Long> inquiryIds = notifications.stream()
                .filter(notification -> notification.getNotificationType() == NotificationType.NEW_INQUIRY)
                .map(Notification::getTargetId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, InquiryType> inquiryTypes = new HashMap<>();
        if (!inquiryIds.isEmpty()) {
            inquiryRepository.findAllById(inquiryIds)
                    .forEach(inquiry -> inquiryTypes.put(inquiry.getInquiryId(), inquiry.getInquiryType()));
        }

        return notifications.stream()
                .map(notification -> NotificationResDto.fromEntity(notification, Type.ADMIN,
                        notification.getNotificationType() == NotificationType.NEW_INQUIRY
                                ? inquiryTypes.get(notification.getTargetId()) : null))
                .collect(Collectors.toList());
    }
}

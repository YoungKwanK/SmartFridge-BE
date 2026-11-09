package com.be16_2nd.SmartFridge.inquiry.service;

import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.common.service.S3Uploader;
import com.be16_2nd.SmartFridge.food.repository.FoodRepository;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.inquiry.domain.Inquiry;
import com.be16_2nd.SmartFridge.inquiry.domain.InquiryType;
import com.be16_2nd.SmartFridge.inquiry.dto.InquiryCreateDto;
import com.be16_2nd.SmartFridge.inquiry.dto.InquiryResDto;
import com.be16_2nd.SmartFridge.inquiry.repository.InquiryRepository;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.member.repository.MemberRepository;
import com.be16_2nd.SmartFridge.member.service.AdminAccountProvider;
import com.be16_2nd.SmartFridge.member.service.AdminService;
import com.be16_2nd.SmartFridge.notification.domain.Notification;
import com.be16_2nd.SmartFridge.notification.domain.NotificationType;
import com.be16_2nd.SmartFridge.notification.domain.TargetType;
import com.be16_2nd.SmartFridge.notification.dto.NotificationResDto;
import com.be16_2nd.SmartFridge.notification.repository.NotificationRepository;
import com.be16_2nd.SmartFridge.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
 * 관리자 화면에 필요한 문의의 냉장고·작성자 정보와 새 문의 알림의 문의 유형 단위 테스트.
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class InquiryAdminInfoTest {

    @Mock InquiryRepository inquiryRepository;
    @Mock MemberRepository memberRepository;
    @Mock NotificationService notificationService;
    @Mock S3Uploader s3Uploader;
    @Mock AdminAccountProvider adminAccountProvider;
    @Mock FridgeAccessValidator fridgeAccessValidator;
    @Mock NotificationRepository notificationRepository;
    @Mock FoodRepository foodRepository;

    @InjectMocks InquiryService inquiryService;
    @InjectMocks AdminService adminService;

    final Member writer = Member.builder().id(UUID.randomUUID()).name("김지현").email("writer@test.com").build();
    final Member admin = Member.builder().id(UUID.randomUUID()).name("관리자").email("admin@test.com").build();
    final Fridge fridge = Fridge.builder().id(7L).fridgeName("우리집 냉장고").build();

    @BeforeEach
    void login() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(writer.getEmail(), null, List.of()));
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 문의를_등록하면_현재_냉장고를_함께_저장한다() {
        givenWriterAndAdmin();
        when(fridgeAccessValidator.validate(7L))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, writer, Type.COMMON));

        inquiryService.create(createDto(7L));

        ArgumentCaptor<Inquiry> saved = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiryRepository).save(saved.capture());
        assertThat(saved.getValue().getFridge()).isSameAs(fridge);
    }

    @Test
    void 냉장고_없이도_문의를_등록할_수_있다() {
        givenWriterAndAdmin();

        inquiryService.create(createDto(null));

        ArgumentCaptor<Inquiry> saved = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiryRepository).save(saved.capture());
        assertThat(saved.getValue().getFridge()).isNull();
        verify(fridgeAccessValidator, never()).validate(any());
    }

    @Test
    void 속하지_않은_냉장고로는_문의를_등록할_수_없다() {
        givenWriterAndAdmin();
        when(fridgeAccessValidator.validate(7L)).thenThrow(new AccessDeniedException("권한 없음"));

        assertThatThrownBy(() -> inquiryService.create(createDto(7L)))
                .isInstanceOf(AccessDeniedException.class);

        verify(inquiryRepository, never()).save(any());
    }

    @Test
    void 문의_응답에_작성자_이름과_냉장고_이름이_포함된다() {
        InquiryResDto dto = InquiryResDto.fromEntity(Inquiry.builder().member(writer).fridge(fridge).build());

        assertThat(dto.getMemberName()).isEqualTo("김지현");
        assertThat(dto.getMemberEmail()).isEqualTo("writer@test.com");
        assertThat(dto.getFridgeName()).isEqualTo("우리집 냉장고");
    }

    @Test
    void 냉장고_정보가_없는_문의는_냉장고_값이_null이다() {
        InquiryResDto dto = InquiryResDto.fromEntity(Inquiry.builder().member(writer).build());

        assertThat(dto.getFridgeId()).isNull();
        assertThat(dto.getFridgeName()).isNull();
    }

    @Test
    void 새_문의_알림에_사용자가_선택한_문의_유형을_담는다() {
        givenAdminLoggedIn(List.of(notification(NotificationType.NEW_INQUIRY, 10L)));
        when(inquiryRepository.findAllById(List.of(10L)))
                .thenReturn(List.of(Inquiry.builder().inquiryId(10L).inquiryType(InquiryType.BUG).build()));

        List<NotificationResDto> result = adminService.findAllNotification();

        assertThat(result.get(0).getInquiryType()).isEqualTo(InquiryType.BUG);
    }

    @Test
    void 삭제된_문의의_알림은_문의_유형이_없어도_조회된다() {
        givenAdminLoggedIn(List.of(notification(NotificationType.NEW_INQUIRY, 10L)));
        when(inquiryRepository.findAllById(List.of(10L))).thenReturn(List.of());

        assertThat(adminService.findAllNotification().get(0).getInquiryType()).isNull();
    }

    @Test
    void 새_문의가_아닌_알림은_문의를_조회하지_않는다() {
        givenAdminLoggedIn(List.of(notification(NotificationType.ADMIN_REPLY, 10L)));

        assertThat(adminService.findAllNotification().get(0).getInquiryType()).isNull();
        verify(inquiryRepository, never()).findAllById(any());
    }

    private void givenWriterAndAdmin() {
        when(memberRepository.findByEmail(writer.getEmail())).thenReturn(Optional.of(writer));
        when(adminAccountProvider.getAdmin()).thenReturn(admin);
    }

    private void givenAdminLoggedIn(List<Notification> notifications) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin.getEmail(), null, List.of()));
        when(memberRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(notificationRepository.findByReceiverOrderByIsReadAscCreatedTimeDesc(admin)).thenReturn(notifications);
    }

    private Notification notification(NotificationType type, Long targetId) {
        return Notification.builder()
                .id(1L)
                .receiver(admin)
                .content("내용")
                .notificationType(type)
                .targetType(TargetType.INQUIRY)
                .targetId(targetId)
                .build();
    }

    private InquiryCreateDto createDto(Long fridgeId) {
        InquiryCreateDto dto = new InquiryCreateDto();
        dto.setInquiryType(InquiryType.BUG);
        dto.setTitle("제목");
        dto.setContents("내용");
        dto.setFridgeId(fridgeId);
        return dto;
    }
}

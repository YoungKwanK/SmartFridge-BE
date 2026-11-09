package com.be16_2nd.SmartFridge.notification.service;

import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.inquiry.domain.Inquiry;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.notification.domain.Notification;
import com.be16_2nd.SmartFridge.notification.domain.NotificationType;
import com.be16_2nd.SmartFridge.notification.dto.NotificationResDto;
import com.be16_2nd.SmartFridge.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 새 문의 알림의 냉장고 저장과 알림 응답 JSON 단위 테스트.
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationInquiryFridgeTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationPublisher notificationPublisher;
    @Mock NotificationSettingService notificationSettingService;

    @InjectMocks NotificationService notificationService;

    final Member writer = Member.builder().id(UUID.randomUUID()).name("김지현").email("writer@test.com").build();
    final Member admin = Member.builder().id(UUID.randomUUID()).name("관리자").email("admin@test.com").build();
    final Fridge fridge = Fridge.builder().id(7L).fridgeName("우리집 냉장고").build();

    @Test
    void 새_문의_알림에_문의의_냉장고를_저장한다() {
        notificationService.create(writer, admin, NotificationType.NEW_INQUIRY,
                Inquiry.builder().inquiryId(1L).member(writer).fridge(fridge).build());

        assertThat(savedNotification().getFridge()).isSameAs(fridge);
    }

    @Test
    void 냉장고가_없는_문의의_알림은_냉장고_없이_저장한다() {
        notificationService.create(writer, admin, NotificationType.NEW_INQUIRY,
                Inquiry.builder().inquiryId(1L).member(writer).build());

        assertThat(savedNotification().getFridge()).isNull();
    }

    @Test
    void 알림_응답_JSON에_읽음_여부와_냉장고_번호가_포함된다() {
        Notification notification = Notification.builder()
                .id(1L).receiver(admin).fridge(fridge).content("내용")
                .notificationType(NotificationType.NEW_INQUIRY).isRead(true).build();

        JsonNode json = new JsonMapper().valueToTree(NotificationResDto.fromEntity(notification, Type.ADMIN));

        assertThat(json.get("read").asBoolean()).isTrue();
        assertThat(json.get("fridgeId").asLong()).isEqualTo(7L);
    }

    private Notification savedNotification() {
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        return saved.getValue();
    }
}

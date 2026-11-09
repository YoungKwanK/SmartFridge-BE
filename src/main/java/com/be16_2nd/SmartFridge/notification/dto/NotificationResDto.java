package com.be16_2nd.SmartFridge.notification.dto;

import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.inquiry.domain.InquiryType;
import com.be16_2nd.SmartFridge.notification.domain.Notification;
import com.be16_2nd.SmartFridge.notification.domain.NotificationType;
import com.be16_2nd.SmartFridge.notification.domain.TargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationResDto {
    private Long id;
    private String content;
    private boolean isRead;
    private NotificationType type;
    private Type fridgeMemberType;
    private TargetType targetType;
    private Long targetId;
    private Long fridgeId;
    private LocalDateTime createdAt;
    // 새 문의 알림일 때 사용자가 선택한 문의 유형 (관리자 알림 목록에서만 사용)
    private InquiryType inquiryType;

    public static NotificationResDto fromEntity(Notification notification, Type fridgeMemberType, InquiryType inquiryType) {
        NotificationResDto dto = fromEntity(notification, fridgeMemberType);
        dto.setInquiryType(inquiryType);
        return dto;
    }

    public static NotificationResDto fromEntity(Notification notification, Type fridgeMemberType) {
        return NotificationResDto.builder()
                .id(notification.getId())
                .content(notification.getContent())
                .isRead(notification.isRead())
                .type(notification.getNotificationType())
                .fridgeMemberType(fridgeMemberType)
                .targetType(notification.getTargetType())
                .targetId(notification.getTargetId())
                .fridgeId(notification.getFridge() != null ? notification.getFridge().getId() : null)
                .createdAt(notification.getCreatedTime())
                .build();
    }
}

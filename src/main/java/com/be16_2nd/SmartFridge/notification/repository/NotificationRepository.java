package com.be16_2nd.SmartFridge.notification.repository;

import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.notification.domain.Notification;
import com.be16_2nd.SmartFridge.notification.domain.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAll(Specification<Notification> specification, Pageable pageable);

    // Admin 알림 조회를 위한 새로운 메서드들

    // Admin의 안읽은 알림 조회 (최신순 정렬)
    List<Notification> findByReceiverAndIsReadFalseOrderByCreatedTimeDesc(Member receiver);

    // Admin의 읽은 알림 조회 (최신순 정렬)
    List<Notification> findByReceiverAndIsReadTrueOrderByCreatedTimeDesc(Member receiver);

    // Admin의 모든 알림 조회 (읽음 상태별 정렬)
    List<Notification> findByReceiverOrderByIsReadAscCreatedTimeDesc(Member receiver);

    // Admin의 안읽은 알림 개수 조회
    long countByReceiverAndIsReadFalse(Member receiver);

    List<Notification> findTop5ByReceiverAndFridgeIdAndIsReadFalseAndNotificationTypeInOrderByCreatedTimeDesc(
            Member receiver,
            Long fridgeId,
            List<NotificationType> notificationTypes
    );

    // 본인 알림만 조회 (다른 사용자의 알림 id 로는 찾을 수 없음)
    Optional<Notification> findByIdAndReceiver(Long id, Member receiver);

    // 본인 알림 중 안 읽은 것만 한 번에 읽음 처리
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.isRead = true " +
            "where n.id in :ids and n.receiver = :receiver and n.isRead = false")
    int markAsRead(@Param("ids") List<Long> ids, @Param("receiver") Member receiver);

    // 특정 대상의 알림을 한 번에 읽음 처리 (같은 트랜잭션에서 읽어 둔 엔티티를 쓸 수 있도록 영속성 컨텍스트를 비우지 않음)
    @Modifying
    @Query("update Notification n set n.isRead = true " +
            "where n.receiver = :receiver and n.notificationType = :type " +
            "and n.targetId = :targetId and n.isRead = false")
    int markAsReadByTarget(@Param("receiver") Member receiver,
                           @Param("type") NotificationType type,
                           @Param("targetId") Long targetId);
}

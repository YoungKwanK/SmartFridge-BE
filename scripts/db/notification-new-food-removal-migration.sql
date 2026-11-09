-- 식품 등록 알림(NEW_FOOD) 제거
-- NEW_FOOD 행이 남아 있으면 새 코드에서 알림 조회가 실패하므로 1번은 배포 전에 실행한다.
-- notification-type-reply-updated-migration.sql 을 먼저 적용한 DB 기준이다.

-- 1. 기존 식품 등록 알림 삭제 (되돌릴 수 없음, 필요하면 먼저 백업)
SELECT COUNT(*) FROM notification WHERE notification_type = 'NEW_FOOD';
DELETE FROM notification WHERE notification_type = 'NEW_FOOD';

-- 2. 컬럼 타입 확인 (Type 이 enum(...) 이 아니면 3번은 실행하지 않는다)
SHOW COLUMNS FROM notification LIKE 'notification_type';

-- 3. ENUM 값 목록에서 NEW_FOOD 제거 (배포 후 실행, 이전 코드가 동작 중이면 식품 등록이 실패한다)
ALTER TABLE notification
    MODIFY notification_type ENUM (
        'NEW_MEMBER', 'EXPIRATION_IMMINENT', 'NEW_ANNOUNCEMENT', 'NEW_COMMENT',
        'NEW_INQUIRY', 'ADMIN_REPLY', 'ADMIN_REPLY_UPDATED', 'MANAGER_CHAT', 'GROUP_CHAT',
        'ROOM_FULL', 'ROOM_CLOSED'
    ) NOT NULL;

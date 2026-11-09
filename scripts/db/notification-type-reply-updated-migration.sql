-- 답변 수정 알림(ADMIN_REPLY_UPDATED) 추가
-- ddl-auto: update 는 기존 ENUM 컬럼의 값 목록을 바꾸지 않으므로 배포 전에 운영 DB에서 확인한다.

-- 1. 컬럼 타입 확인 (Type 이 enum(...) 이 아니면 2번은 실행하지 않는다)
SHOW COLUMNS FROM notification LIKE 'notification_type';

-- 2. ENUM 값 목록에 새 알림 타입 추가
ALTER TABLE notification
    MODIFY notification_type ENUM (
        'NEW_FOOD', 'NEW_MEMBER', 'EXPIRATION_IMMINENT', 'NEW_ANNOUNCEMENT', 'NEW_COMMENT',
        'NEW_INQUIRY', 'ADMIN_REPLY', 'ADMIN_REPLY_UPDATED', 'MANAGER_CHAT', 'GROUP_CHAT',
        'ROOM_FULL', 'ROOM_CLOSED'
    ) NOT NULL;

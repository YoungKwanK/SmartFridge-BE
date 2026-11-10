-- 공동구매 글(purchase_post)을 게시판 글(post)과 1:1로 연결하는 이관 스크립트 (MariaDB)
--
-- 바뀌는 점
--   purchase_post.post_id (NULL 허용, UNIQUE, ON DELETE SET NULL) 추가
--   purchase_post.contents 삭제 → 본문은 post.content 로 옮긴다
--   기존 공동구매 글마다 post(category = '공동구매') 를 하나씩 만들어 연결한다
--
-- 운영(prod)은 ddl-auto: update 라서 post_id 컬럼은 앱 기동 시 생기지만, 기존 공동구매 글에 게시글을
-- 만들어 주지 않고 contents 컬럼도 지우지 않는다. 그래서 이 스크립트는 "새 버전을 띄우기 전에" 실행한다.
--
-- 전제: post.category 컬럼이 있어야 한다 (게시판 카테고리 enum 변경이 먼저 반영된 상태)
--
-- 실행 순서
--   0) DB 백업 (mysqldump)
--   1) 백엔드 중지 (공동구매 방이 새로 생기지 않게)
--   2) [1] ~ [4] 순서대로 실행, [5] 검증 쿼리로 건수 확인
--   3) 새 버전 배포
--   4) 문제가 없으면 [6] 으로 contents 컬럼 삭제


-- [1] post_id 컬럼 추가 + 이관용 임시 컬럼
ALTER TABLE purchase_post ADD COLUMN post_id BIGINT NULL;
ALTER TABLE post ADD COLUMN legacy_purchase_post_id BIGINT NULL;


-- [2] 공동구매 글마다 게시판 글 생성 (작성자 = 방장, 냉장고 = 채팅방의 냉장고, 작성 시각 = 방 개설 시각)
INSERT INTO post (title, content, view_count, comment_count, member_id, category, pinned, pinned_at,
                  fridge_id, created_time, updated_time, legacy_purchase_post_id)
SELECT pp.title, COALESCE(pp.contents, ''), 0, 0, pp.creator_id, '공동구매', 0, NULL,
       cr.fridge_id, pp.created_time, pp.created_time, pp.id
FROM purchase_post pp
JOIN chat_room cr ON cr.id = pp.chat_room_id
WHERE pp.post_id IS NULL;


-- [3] 만든 게시글과 연결
UPDATE purchase_post pp
JOIN post p ON p.legacy_purchase_post_id = pp.id
SET pp.post_id = p.id;


-- [4] 제약 추가 (게시글을 지우면 공동구매 정보는 남기고 post_id 만 NULL) + 임시 컬럼 삭제
ALTER TABLE purchase_post
    ADD CONSTRAINT uk_purchase_post_post UNIQUE (post_id),
    ADD CONSTRAINT fk_purchase_post_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE SET NULL;
ALTER TABLE post DROP COLUMN legacy_purchase_post_id;


-- [5] 검증: 두 값이 같고, 연결 안 된 공동구매 글은 0이어야 한다
SELECT (SELECT COUNT(*) FROM purchase_post) AS purchase_posts,
       (SELECT COUNT(*) FROM purchase_post WHERE post_id IS NOT NULL) AS linked_purchase_posts;
SELECT pp.id, pp.title
FROM purchase_post pp
LEFT JOIN post p ON p.id = pp.post_id
WHERE p.id IS NULL;
-- 참고: 이관 전에 게시판에서 직접 쓴 '공동구매' 글(모집 정보·채팅방 없음)은 그대로 남는다
SELECT p.id, p.title
FROM post p
LEFT JOIN purchase_post pp ON pp.post_id = p.id
WHERE p.category = '공동구매' AND pp.id IS NULL;


-- [6] 정리 (새 버전 확인 후): 본문은 post.content 로 옮겼으므로 삭제
-- ALTER TABLE purchase_post DROP COLUMN contents;

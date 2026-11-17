-- [로컬 전용] 리포트 화면 확인용 더미 데이터
-- 운영 DB에서는 절대 실행하지 않는다.
--
-- 사용법
--   1. 서버를 한 번 띄워 테이블(food, food_history)이 만들어진 상태에서 실행한다.
--   2. 아래 @email 을 내 로그인 이메일로 바꾼다. 그 회원이 들어가 있는 첫 번째 냉장고에 넣는다.
--      다른 냉장고에 넣고 싶으면 @fridge_id 를 숫자로 직접 넣는다.
--   3. 전체를 실행한다. 날짜는 실행한 날 기준으로 이번 달, 지난달, 두 달 전에 나뉘어 들어간다.
--   ※ 로컬도 ddl-auto: update 라서 예전에 만든 DB면 storage_type 에 실온이 없을 수 있다.
--     "Data truncated for column 'storage_type'" 오류가 나면 food-storage-type-room-temperature-migration.sql 을 먼저 실행한다.
--
-- 들어가는 것
--   food_history 24건: 내가 등록한 기록 + 등록자 없는 기록(냉장고 전체 리포트에서만 보임)
--   food 6건: 지금 냉장고에 남아 있는 식품 (유통기한 지남 2, 임박 2, 여유 2)

SET @email = 'me@example.com';

SET @member_id = (SELECT id FROM member WHERE email = @email);
SET @fridge_id = (SELECT fm.fridge_id FROM fridge_member fm
                  WHERE fm.member_id = @member_id
                  ORDER BY fm.fridge_id LIMIT 1);

-- 값이 비어 있으면 이메일이나 냉장고 참여를 확인한다
SELECT @member_id AS member_id, @fridge_id AS fridge_id;

-- 각 달 1일 0시
SET @m0 = DATE_FORMAT(NOW(), '%Y-%m-01');
SET @m1 = DATE_FORMAT(NOW() - INTERVAL 1 MONTH, '%Y-%m-01');
SET @m2 = DATE_FORMAT(NOW() - INTERVAL 2 MONTH, '%Y-%m-01');

-- ===== food_history =====
-- ended_at: 처리한 시각 / storage_start_date: 등록일 / expiration_date_time: 유통기한
-- 이번 달 기록은 오늘을 넘지 않게 LEAST(..., NOW()) 로 자른다

INSERT INTO food_history
    (fridge_id, member_id, name, category, storage_type, quantity, is_shared,
     storage_start_date, expiration_date_time, result, ended_at)
VALUES
-- 두 달 전: 소비 11, 폐기 7 → 폐기율 약 39%
(@fridge_id, @member_id, '우유',     '유제품',   '냉장', 2, 1, @m2 + INTERVAL 0 DAY,  @m2 + INTERVAL 7 DAY + INTERVAL 23 HOUR,  '소비', @m2 + INTERVAL 4 DAY + INTERVAL 12 HOUR),
(@fridge_id, @member_id, '요구르트', '유제품',   '냉장', 3, 0, @m2 + INTERVAL 1 DAY,  @m2 + INTERVAL 6 DAY + INTERVAL 23 HOUR,  '폐기', @m2 + INTERVAL 12 DAY + INTERVAL 12 HOUR),
(@fridge_id, @member_id, '시금치',   '채소',     '냉장', 1, 0, @m2 + INTERVAL 2 DAY,  @m2 + INTERVAL 5 DAY + INTERVAL 23 HOUR,  '폐기', @m2 + INTERVAL 10 DAY + INTERVAL 12 HOUR),
(@fridge_id, @member_id, '계란',     '기타',     '냉장', 5, 1, @m2 + INTERVAL 3 DAY,  @m2 + INTERVAL 20 DAY + INTERVAL 23 HOUR, '소비', @m2 + INTERVAL 9 DAY + INTERVAL 12 HOUR),
(@fridge_id, @member_id, '사과',     '과일',     '실온', 2, 1, @m2 + INTERVAL 5 DAY,  @m2 + INTERVAL 15 DAY + INTERVAL 23 HOUR, '소비', @m2 + INTERVAL 8 DAY + INTERVAL 12 HOUR),
(@fridge_id, @member_id, '두부',     '가공식품', '냉장', 2, 0, @m2 + INTERVAL 6 DAY,  @m2 + INTERVAL 10 DAY + INTERVAL 23 HOUR, '폐기', @m2 + INTERVAL 14 DAY + INTERVAL 12 HOUR),
(@fridge_id, NULL,       '상추',     '채소',     '냉장', 1, 1, @m2 + INTERVAL 7 DAY,  @m2 + INTERVAL 10 DAY + INTERVAL 23 HOUR, '폐기', @m2 + INTERVAL 16 DAY + INTERVAL 12 HOUR),
(@fridge_id, NULL,       '콜라',     '음료',     '실온', 2, 1, @m2 + INTERVAL 8 DAY,  @m2 + INTERVAL 90 DAY + INTERVAL 23 HOUR, '소비', @m2 + INTERVAL 11 DAY + INTERVAL 12 HOUR),

-- 지난달: 소비 12, 폐기 5 → 폐기율 약 29%
(@fridge_id, @member_id, '우유',     '유제품',   '냉장', 2, 1, @m1 + INTERVAL 0 DAY,  @m1 + INTERVAL 7 DAY + INTERVAL 23 HOUR,  '소비', @m1 + INTERVAL 3 DAY + INTERVAL 12 HOUR),
(@fridge_id, @member_id, '요구르트', '유제품',   '냉장', 4, 0, @m1 + INTERVAL 1 DAY,  @m1 + INTERVAL 8 DAY + INTERVAL 23 HOUR,  '소비', @m1 + INTERVAL 6 DAY + INTERVAL 12 HOUR),
(@fridge_id, @member_id, '요구르트', '유제품',   '냉장', 2, 0, @m1 + INTERVAL 1 DAY,  @m1 + INTERVAL 8 DAY + INTERVAL 23 HOUR,  '폐기', @m1 + INTERVAL 13 DAY + INTERVAL 12 HOUR),
(@fridge_id, @member_id, '삼겹살',   '고기',     '냉동', 1, 1, @m1 + INTERVAL 2 DAY,  @m1 + INTERVAL 30 DAY + INTERVAL 23 HOUR, '소비', @m1 + INTERVAL 9 DAY + INTERVAL 12 HOUR),
(@fridge_id, @member_id, '시금치',   '채소',     '냉장', 1, 0, @m1 + INTERVAL 4 DAY,  @m1 + INTERVAL 8 DAY + INTERVAL 23 HOUR,  '폐기', @m1 + INTERVAL 11 DAY + INTERVAL 12 HOUR),
(@fridge_id, @member_id, '라면',     '가공식품', '실온', 3, 1, @m1 + INTERVAL 5 DAY,  @m1 + INTERVAL 120 DAY + INTERVAL 23 HOUR, '소비', @m1 + INTERVAL 15 DAY + INTERVAL 12 HOUR),
(@fridge_id, @member_id, '바나나',   '과일',     '실온', 2, 1, @m1 + INTERVAL 7 DAY,  @m1 + INTERVAL 11 DAY + INTERVAL 23 HOUR, '폐기', @m1 + INTERVAL 10 DAY + INTERVAL 12 HOUR),
(@fridge_id, NULL,       '오렌지주스', '음료',   '냉장', 2, 1, @m1 + INTERVAL 8 DAY,  @m1 + INTERVAL 20 DAY + INTERVAL 23 HOUR, '소비', @m1 + INTERVAL 12 DAY + INTERVAL 12 HOUR),

-- 이번 달: 소비 14, 폐기 4 → 폐기율 약 22%
(@fridge_id, @member_id, '우유',     '유제품',   '냉장', 2, 1, @m0 - INTERVAL 2 DAY,  @m0 + INTERVAL 5 DAY + INTERVAL 23 HOUR,  '소비', LEAST(@m0 + INTERVAL 2 DAY + INTERVAL 12 HOUR, NOW())),
(@fridge_id, @member_id, '요구르트', '유제품',   '냉장', 3, 0, @m0 + INTERVAL 0 DAY,  @m0 + INTERVAL 7 DAY + INTERVAL 23 HOUR,  '소비', LEAST(@m0 + INTERVAL 4 DAY + INTERVAL 12 HOUR, NOW())),
(@fridge_id, @member_id, '요구르트', '유제품',   '냉장', 2, 0, @m0 + INTERVAL 0 DAY,  @m0 + INTERVAL 7 DAY + INTERVAL 23 HOUR,  '폐기', LEAST(@m0 + INTERVAL 9 DAY + INTERVAL 12 HOUR, NOW())),
(@fridge_id, @member_id, '계란',     '기타',     '냉장', 3, 1, @m0 + INTERVAL 1 DAY,  @m0 + INTERVAL 21 DAY + INTERVAL 23 HOUR, '소비', LEAST(@m0 + INTERVAL 6 DAY + INTERVAL 12 HOUR, NOW())),
(@fridge_id, @member_id, '닭가슴살', '고기',     '냉동', 2, 0, @m0 + INTERVAL 1 DAY,  @m0 + INTERVAL 40 DAY + INTERVAL 23 HOUR, '소비', LEAST(@m0 + INTERVAL 7 DAY + INTERVAL 12 HOUR, NOW())),
(@fridge_id, @member_id, '시금치',   '채소',     '냉장', 1, 0, @m0 + INTERVAL 2 DAY,  @m0 + INTERVAL 5 DAY + INTERVAL 23 HOUR,  '폐기', LEAST(@m0 + INTERVAL 8 DAY + INTERVAL 12 HOUR, NOW())),
(@fridge_id, NULL,       '케이크',   '가공식품', '냉장', 1, 1, @m0 + INTERVAL 3 DAY,  @m0 + INTERVAL 6 DAY + INTERVAL 23 HOUR,  '폐기', LEAST(@m0 + INTERVAL 5 DAY + INTERVAL 12 HOUR, NOW())),
(@fridge_id, NULL,       '탄산수',   '음료',     '실온', 4, 1, @m0 + INTERVAL 3 DAY,  @m0 + INTERVAL 60 DAY + INTERVAL 23 HOUR, '소비', LEAST(@m0 + INTERVAL 8 DAY + INTERVAL 12 HOUR, NOW()));

-- ===== food (지금 냉장고에 남아 있는 식품) =====
INSERT INTO food
    (fridge_id, member_id, name, category, storage_type, quantity, is_shared,
     storage_start_date, expiration_date_time, memo, version)
VALUES
(@fridge_id, @member_id, '두부',     '가공식품', '냉장', 1, 0, CURDATE() - INTERVAL 8 DAY,  NOW() - INTERVAL 3 DAY,  NULL, 0),
(@fridge_id, @member_id, '상추',     '채소',     '냉장', 1, 1, CURDATE() - INTERVAL 5 DAY,  NOW() - INTERVAL 1 DAY,  NULL, 0),
(@fridge_id, @member_id, '요구르트', '유제품',   '냉장', 5, 1, CURDATE() - INTERVAL 2 DAY,  NOW() + INTERVAL 2 DAY,  '공유용', 0),
(@fridge_id, @member_id, '우유',     '유제품',   '냉장', 1, 0, CURDATE() - INTERVAL 1 DAY,  NOW() + INTERVAL 1 DAY,  NULL, 0),
(@fridge_id, @member_id, '만두',     '가공식품', '냉동', 2, 1, CURDATE() - INTERVAL 10 DAY, NOW() + INTERVAL 60 DAY, NULL, 0),
(@fridge_id, @member_id, '라면',     '가공식품', '실온', 4, 1, CURDATE(),                   NOW() + INTERVAL 150 DAY, NULL, 0);

-- 확인
SELECT result, SUM(quantity) FROM food_history WHERE fridge_id = @fridge_id GROUP BY result;

-- 되돌리기 (이 냉장고의 기록과 위 식품을 지운다. 필요할 때만 주석을 풀어 실행)
-- DELETE FROM food_history WHERE fridge_id = @fridge_id;
-- DELETE FROM food WHERE fridge_id = @fridge_id AND name IN ('두부','상추','요구르트','우유','만두','라면');

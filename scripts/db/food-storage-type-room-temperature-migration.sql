-- 식품 보관방식에 실온 추가 (StorageType: 냉장, 냉동, 실온)
-- ddl-auto: update 는 기존 ENUM 컬럼의 값 목록을 바꾸지 않으므로, 새 버전을 띄우기 전에 운영 DB에서 실행한다.
-- 실행하지 않으면 실온으로 등록하거나 실온 식품을 정리(food_history 저장)할 때 오류가 난다.

-- 1. 컬럼 타입 확인 (Type 이 enum(...) 이 아니면 2번은 실행하지 않는다)
SHOW COLUMNS FROM food LIKE 'storage_type';
SHOW COLUMNS FROM food_history LIKE 'storage_type';

-- 2. ENUM 값 목록에 실온 추가 (NULL 허용은 엔티티와 같게 유지)
ALTER TABLE food
    MODIFY storage_type ENUM ('냉장', '냉동', '실온') NULL;

ALTER TABLE food_history
    MODIFY storage_type ENUM ('냉장', '냉동', '실온') NULL;

-- 3. 확인
SHOW COLUMNS FROM food LIKE 'storage_type';
SHOW COLUMNS FROM food_history LIKE 'storage_type';

-- 문의 내용과 답변 내용을 TEXT로 변경 (기존 VARCHAR(255)에서는 255자를 넘으면 저장 실패)
-- ddl-auto: update 는 기존 컬럼 타입을 바꾸지 않으므로 배포 전에 운영 DB에서 실행한다.

ALTER TABLE inquiry MODIFY contents TEXT;
ALTER TABLE inquiry_comment MODIFY comment_contents TEXT;

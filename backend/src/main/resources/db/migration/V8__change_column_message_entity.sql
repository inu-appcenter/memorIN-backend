-- 메세지 엔티티의 컬럼 내용 수정
--
-- 메세지의 타입을 부여하여 전송하는 메세지의 형태를 구분할 수 있게 함.
-- 이를 위하여 보내는 타입의 종류를 텍스트, 이미지, 게시물 공유로 총 3가지의 타입을 생성.
--
CREATE TYPE message_type     AS ENUM ('TEXT', 'IMAGE', 'POST_SHARE');

ALTER TABLE messages ADD COLUMN type message_type NOT NULL DEFAULT 'TEXT';

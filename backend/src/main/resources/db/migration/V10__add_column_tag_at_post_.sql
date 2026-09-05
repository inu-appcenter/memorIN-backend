-- post 엔티티의 컬럼 내용 수정
--
-- 게시물에 선택 가능한 태그를 추가하여 필터링 검색을 가능하게 함.
-- 이를 위하여 보내는 타입의 종류를 텍스트, 이미지, 게시물 공유로 총 3가지의 타입을 생성.
--
ALTER TABLE posts ADD COLUMN tags jsonb NOT NULL DEFAULT '[]';

-- 포함 여부(@>) 조회만 쓸 거라면 jsonb_path_ops가 더 작고 빠릅니다
CREATE INDEX idx_posts_tags_gin ON posts USING GIN (tags jsonb_path_ops);

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_tags_max3
        CHECK (jsonb_array_length(tags) <= 3);

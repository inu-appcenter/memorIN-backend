-- Sprint 0 초안 DDL — 팀 리뷰 전 임시 파일
-- 실제 확정 DDL은 수요일 DB 스키마 리뷰 이후 작성

-- UUID v7: PostgreSQL 18 내장 uuidv7() 사용 → 별도 확장/라이브러리 불필요.
--          앱(Hibernate)에서도 v7을 생성하므로 아래 DEFAULT는 SQL 직접 INSERT용 안전망.

-- ENUM 타입
-- 문자열로 저장하면 오타 발생 시 대응하기 어렵기 때문에 ENUM으로 선언
-- 각 타입이 유효범위 밖의 값이 저장되는 것을 막는다.
CREATE TYPE visibility_type AS ENUM ('PUBLIC', 'FRIENDS', 'PRIVATE');
CREATE TYPE timeslot_type    AS ENUM ('AM', 'PM');
CREATE TYPE follow_status    AS ENUM ('PENDING', 'ACCEPTED', 'BLOCKED');
CREATE TYPE chat_type        AS ENUM ('DIRECT', 'GROUP');
CREATE TYPE member_role      AS ENUM ('OWNER', 'MEMBER');

-- users
CREATE TABLE IF NOT EXISTS users (
    id                UUID         PRIMARY KEY DEFAULT uuidv7(),
    email             VARCHAR(320) NOT NULL UNIQUE, -- email 표준 길이 320자
    password_hash     VARCHAR(255), -- NULL 허용: 소셜/학번(INU SSO) 로그인 유저는 비밀번호 없음
    username          VARCHAR(50)  NOT NULL UNIQUE,
    display_name      VARCHAR(100) NOT NULL,
    bio               TEXT,
    profile_image_key VARCHAR(500), -- 이미지 파일 자체를 db에 저장하는 것이 아니라 Minio에 저장된 경로 키만 저장
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_users_email    ON users (email)    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_users_username ON users (username)  WHERE deleted_at IS NULL;

-- posts
CREATE TABLE IF NOT EXISTS posts (
    id            UUID            PRIMARY KEY DEFAULT uuidv7(),
    user_id       UUID            NOT NULL REFERENCES users(id),
    content       JSONB           NOT NULL DEFAULT '[]',
    visibility    visibility_type NOT NULL DEFAULT 'PUBLIC',
    timeslot      timeslot_type   NOT NULL,
    recorded_date DATE            NOT NULL DEFAULT CURRENT_DATE,
    view_count    INTEGER         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_posts_user_id     ON posts (user_id, recorded_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_posts_content_gin ON posts USING GIN (content);

-- post_media
CREATE TABLE IF NOT EXISTS post_media (
    id              UUID         PRIMARY KEY DEFAULT uuidv7(),
    post_id         UUID         NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    file_key        VARCHAR(500) NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT,
    order_index     SMALLINT     NOT NULL DEFAULT 0,
    width           INTEGER,
    height          INTEGER,
    duration_sec    INTEGER,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  -- 게시물 소프트 삭제 시 함께 채움. Quota 집계/파일 GC는 deleted_at IS NULL만 대상
);

CREATE INDEX IF NOT EXISTS idx_post_media_post_id ON post_media (post_id, order_index) WHERE deleted_at IS NULL;

-- pending_uploads: presigned 업로드 예약. TOCTOU 방지(committed+pending 합산으로 quota 검증) +
-- 고아 오브젝트 정리용 TTL. 게시물에 커밋되면(첨부로 확정) 행을 지우고, 커밋 없이 만료되면
-- 정리 배치가 MinIO 오브젝트와 함께 지운다.
CREATE TABLE IF NOT EXISTS pending_uploads (
    id             UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    object_key     VARCHAR(500) NOT NULL UNIQUE,
    reserved_bytes BIGINT       NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pending_uploads_user_id    ON pending_uploads (user_id);
CREATE INDEX IF NOT EXISTS idx_pending_uploads_expires_at ON pending_uploads (expires_at);

-- post_likes (좋아요)
CREATE TABLE IF NOT EXISTS post_likes (
    id         UUID        PRIMARY KEY DEFAULT uuidv7(),
    post_id    UUID        NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_post_like UNIQUE (post_id, user_id) -- 한 사람이 한 게시물에 한 번만
);

CREATE INDEX IF NOT EXISTS idx_post_likes_post ON post_likes (post_id);

-- post_comments (댓글 / 대댓글)
CREATE TABLE IF NOT EXISTS post_comments (
    id         UUID        PRIMARY KEY DEFAULT uuidv7(),
    post_id    UUID        NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id),
    parent_id  UUID        REFERENCES post_comments(id) ON DELETE CASCADE, -- 대댓글 부모 (최상위는 NULL)
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_post_comments_post   ON post_comments (post_id, created_at) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_post_comments_parent ON post_comments (parent_id)           WHERE parent_id IS NOT NULL;

-- follows
CREATE TABLE IF NOT EXISTS follows (
    id           UUID         PRIMARY KEY DEFAULT uuidv7(),
    follower_id  UUID         NOT NULL REFERENCES users(id),
    following_id UUID         NOT NULL REFERENCES users(id),
    status       follow_status NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_follows        UNIQUE (follower_id, following_id), -- 중복 팔로우x
    CONSTRAINT chk_no_self_follow CHECK (follower_id <> following_id) -- 자기자신 팔로우x
);

CREATE INDEX IF NOT EXISTS idx_follows_follower  ON follows (follower_id,  status);
CREATE INDEX IF NOT EXISTS idx_follows_following ON follows (following_id, status);

-- chat_rooms
CREATE TABLE IF NOT EXISTS chat_rooms (
    id            UUID        PRIMARY KEY DEFAULT uuidv7(),
    name          VARCHAR(100),
    type          chat_type   NOT NULL DEFAULT 'DIRECT',
    thumbnail_key VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- chat_room_members
CREATE TABLE IF NOT EXISTS chat_room_members (
    id           UUID        PRIMARY KEY DEFAULT uuidv7(),
    room_id      UUID        NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users(id),
    role         member_role NOT NULL DEFAULT 'MEMBER',
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    left_at      TIMESTAMPTZ,
    CONSTRAINT uq_room_member UNIQUE (room_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_members_user_id ON chat_room_members (user_id) WHERE left_at IS NULL;

-- messages
CREATE TABLE IF NOT EXISTS messages (
    id         UUID        PRIMARY KEY DEFAULT uuidv7(),
    room_id    UUID        NOT NULL REFERENCES chat_rooms(id),
    sender_id  UUID        NOT NULL REFERENCES users(id),
    content    JSONB       NOT NULL,
    sent_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_messages_room_id ON messages (room_id, sent_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE refresh_token (
    user_id       UUID         PRIMARY KEY,
    refresh_token VARCHAR(500) NOT NULL,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

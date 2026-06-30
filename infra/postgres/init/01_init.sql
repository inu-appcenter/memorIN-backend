-- Sprint 0 초안 DDL — 팀 리뷰 전 임시 파일
-- 실제 확정 DDL은 수요일 DB 스키마 리뷰 이후 작성

-- 확장
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ENUM 타입
-- 문자열로 저장하면 오타 발생 시 대응하기 어렵기 때문에 ENUM으로 선언
-- 각 타입이 유효범위 밖의 값이 저장되는 것을 막는다.
CREATE TYPE visibility_type AS ENUM ('public', 'friends', 'private');
CREATE TYPE follow_status    AS ENUM ('pending', 'accepted', 'blocked');
CREATE TYPE chat_type        AS ENUM ('direct', 'group');
CREATE TYPE member_role      AS ENUM ('owner', 'member');

-- users
CREATE TABLE IF NOT EXISTS users (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email             VARCHAR(320) NOT NULL UNIQUE, -- email 표준 길이 320자
    password_hash     VARCHAR(255) NOT NULL,
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
    id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID            NOT NULL REFERENCES users(id),
    content       JSONB           NOT NULL DEFAULT '[]',
    visibility    visibility_type NOT NULL DEFAULT 'public',
    recorded_date DATE            NOT NULL DEFAULT CURRENT_DATE,
    view_count    INTEGER         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_posts_user_date ON posts (user_id, recorded_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_posts_content   ON posts USING GIN (content);

-- post_media
CREATE TABLE IF NOT EXISTS post_media (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID         NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    file_key        VARCHAR(500) NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT,
    order_index     SMALLINT     NOT NULL DEFAULT 0,
    width           INTEGER,
    height          INTEGER,
    duration_sec    INTEGER,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_post_media_post ON post_media (post_id, order_index);

-- follows
CREATE TABLE IF NOT EXISTS follows (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id  UUID         NOT NULL REFERENCES users(id),
    following_id UUID         NOT NULL REFERENCES users(id),
    status       follow_status NOT NULL DEFAULT 'pending',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_follows        UNIQUE (follower_id, following_id), -- 중복 팔로우x
    CONSTRAINT chk_no_self_follow CHECK (follower_id <> following_id) -- 자기자신 팔로우x
);

CREATE INDEX IF NOT EXISTS idx_follows_follower  ON follows (follower_id,  status);
CREATE INDEX IF NOT EXISTS idx_follows_following ON follows (following_id, status);

-- chat_rooms
CREATE TABLE IF NOT EXISTS chat_rooms (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(100),
    type          chat_type   NOT NULL DEFAULT 'direct',
    thumbnail_key VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- chat_room_members
CREATE TABLE IF NOT EXISTS chat_room_members (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id      UUID        NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users(id),
    role         member_role NOT NULL DEFAULT 'member',
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    left_at      TIMESTAMPTZ,
    CONSTRAINT uq_room_member UNIQUE (room_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_members_user ON chat_room_members (user_id) WHERE left_at IS NULL;

-- messages
CREATE TABLE IF NOT EXISTS messages (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id    UUID        NOT NULL REFERENCES chat_rooms(id),
    sender_id  UUID        NOT NULL REFERENCES users(id),
    content    JSONB       NOT NULL,
    sent_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_messages_room ON messages (room_id, sent_at DESC) WHERE deleted_at IS NULL;

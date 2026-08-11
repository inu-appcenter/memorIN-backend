-- FCM 푸시 토큰 (feat: 다중 FCM 토큰 저장 및 갱신 기능 구현)
--
-- 이 테이블도 emoji와 같은 이유로 지금까지 어떤 init/migration SQL에도 존재한 적이 없었다.
-- FcmToken 엔티티는 이미 develop에 있었지만 실제로 적용 가능한 스키마가 한 번도 나온 적이
-- 없었던 것 — Flyway 도입을 기회로 함께 반영한다.
CREATE TABLE fcm_tokens (
    id          UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id     UUID         NOT NULL REFERENCES users(id),
    token       VARCHAR(500) NOT NULL,
    device_type VARCHAR(20)  NOT NULL, -- WEB, ANDROID, IOS (엔티티가 NAMED_ENUM 없이 plain varchar로 매핑)
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fcm_tokens_user_device UNIQUE (user_id, device_type)
);

-- FCM 토큰을 기기 종류가 아니라 토큰 값 기준으로 유일하게 만든다.
--
-- 기존 UNIQUE (user_id, device_type)의 문제:
--   1. 같은 기기에서 A 로그아웃 → B 로그인 시 A·B 두 행이 같은 토큰을 가져, A의 알림(채팅 본문 포함)이 B 기기로 간다.
--   2. 한 사용자가 Android 기기 두 대를 쓰면 두 번째 토큰이 첫 번째를 덮어쓴다.
-- FCM 토큰은 앱 설치 하나를 가리키므로 소유자도 한 명이어야 한다. web_push_subscriptions.endpoint와 같은 규칙이다.

-- 같은 토큰을 가진 행이 여럿이면 가장 최근에 등록·갱신한 행(지금 그 기기를 쓰는 사용자)만 남긴다.
DELETE FROM fcm_tokens older
USING fcm_tokens newer
WHERE older.token = newer.token
  AND (newer.updated_at > older.updated_at
       OR (newer.updated_at = older.updated_at AND newer.id > older.id));

ALTER TABLE fcm_tokens DROP CONSTRAINT uq_fcm_tokens_user_device;
ALTER TABLE fcm_tokens ADD CONSTRAINT uq_fcm_tokens_token UNIQUE (token);

-- 발송·로그아웃·탈퇴는 user_id로 찾는다. 기존 (user_id, device_type) 유일 인덱스가 하던 역할을 대신한다.
CREATE INDEX idx_fcm_tokens_user_id ON fcm_tokens (user_id);

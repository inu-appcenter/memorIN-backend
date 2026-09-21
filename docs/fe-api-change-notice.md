# FE 연동 변경 공지 — #218

배포 시 `POST /auth/refresh`와 `GET /api/follows/requests`의 성공 응답 파싱을 아래 계약으로 교체한다. 두 변경은 기존 응답 구조를 바꾸므로 FE와 같은 배포 단위로 반영한다.

## `POST /auth/refresh`

기존에는 토큰 쌍을 본문 최상단에서 읽었다. 이제 다른 인증 API와 같이 `data` 안에서 읽는다.

```json
{
  "success": true,
  "data": {
    "accessToken": "...",
    "refreshToken": "..."
  },
  "error": null
}
```

## `GET /api/follows/requests`

요청에 `cursor`와 `size`를 선택적으로 추가한다. `size`는 기본 20, 최대 50이다. 기존 배열 응답은 `data.items`로 이동한다.

```json
{
  "success": true,
  "data": {
    "items": [{ "followId": "...", "userId": "..." }],
    "nextCursor": "...",
    "hasNext": true
  },
  "error": null
}
```

`hasNext`가 `true`이면 `nextCursor`를 다음 요청의 `cursor`로 전달한다.

## 팔로우 오류 코드

| 상황 | HTTP | 코드 |
|---|---:|---|
| 사용자를 찾을 수 없음 | 404 | `USER_001` |
| 팔로우 관계 또는 요청을 찾을 수 없음 | 404 | `FOLLOW_001` |
| 요청 상태가 `PENDING`이 아님 | 409 | `FOLLOW_005` |

# 도메인 인터페이스 초안 (Sprint 0 · Week 2 수요일 리뷰용)

> **목적** — Sprint 1 본격 구현에 들어가기 전에, BE 각자가 맡은 도메인이 **서로를 어떻게 호출할지(경계)** 를 미리 합의한다.
> Sprint 0 게이트 체크리스트의 **"BE: 도메인 인터페이스 경계 문서 합의 완료"** 항목을 충족하기 위한 초안이다.
> 이 문서는 **초안**이며, 표의 "합의 필요" 칸을 수요일 세션에서 채워 확정한다.

---

## 1. 도메인 맵 — 누가 무엇을 소유하나

| 도메인 | 담당(안) | 소유 테이블 | 대표 책임 |
| --- | --- | --- | --- |
| **users / auth** | 서윤 · 건희(스키마) | `users` | 회원가입·로그인·JWT, 유저 프로필 |
| **posts / feed** | 도영 | `posts`, `post_media`, `post_likes`, `post_comments` | 게시물 CRUD, 미디어 메타, 좋아요·댓글 |
| **social** | (미정) | `follows` | 팔로우/친구 관계 |
| **chat** | 도영 | `chat_rooms`, `chat_room_members`, `messages` | STOMP 채팅, 방·멤버·메시지 |
| **media / infra** | 기현 | (MinIO, 물리 파일) | Presigned URL 발급, Quota 집계, 파일 GC |

> `users` 테이블은 거의 모든 도메인이 참조하는 **최상위 참조 대상**이다. 여기 경계를 잘 잡는 것이 이 문서의 핵심.

---

## 2. 도메인 간 참조 관계 (DDL FK 기준, 현재 상태)

| 참조하는 쪽(내 도메인) | 참조 대상 | 연결 컬럼(FK) | 현재 구현 상태 | 참조 방식 (🔴 합의 필요) |
| --- | --- | --- | --- | --- |
| posts | users | `posts.user_id` | `Post.userId` 가 **`User` 엔티티 직접 참조**(@ManyToOne) | 엔티티 직접? / UUID만? |
| post_media | posts | `post_media.post_id` | `PostMedia.postId` 가 **`Post` 엔티티 직접 참조** | 엔티티 직접? / UUID만? |
| post_likes | posts, users | `post_id`, `user_id` | 엔티티 미구현 | ─ |
| post_comments | posts, users, (self) | `post_id`, `user_id`, `parent_id` | 엔티티 미구현 | ─ |
| follows | users(×2) | `follower_id`, `following_id` | 엔티티 미구현 | ─ |
| chat_room_members | chat_rooms, users | `room_id`, `user_id` | 엔티티 미구현 | ─ |
| messages | chat_rooms, users | `room_id`, `sender_id` | 엔티티 미구현 | ─ |

---

## 3. 🔴 합의가 필요한 핵심 결정사항 (수요일 안건)

- [ ] **A. 유저 엔티티 단일화** — 현재 `member.entity.Member` 와 `domain.users.Entity.User` 가 **동일 필드로 중복**. 게시물·채팅 등이 참조할 유저 엔티티를 **하나로 확정**한다.
  - 결정: 표준 엔티티 = `____________` / 삭제할 것 = `____________`
- [ ] **B. 도메인 간 참조 방식** — 남의 엔티티를 JPA로 **직접 참조**할지, **ID(UUID)만** 들고 서비스로 조회할지 팀 표준을 정한다.
  - 옵션1: `@ManyToOne User user` (JPA 직접 참조 — 조인 편함, 도메인 결합 강함)
  - 옵션2: `UUID userId` 필드 + 필요 시 `UserQueryService`로 조회 (결합 약함, 조인 직접 관리)
  - 결정: `____________`
- [ ] **C. 필드 네이밍 컨벤션** — Java 필드는 카멜케이스, `@Column(name=...)` 는 스네이크케이스(DB 컬럼)로 통일. (이미 `domain` 패키지 엔티티 적용 완료)
- [ ] **D. 패키지 구조** — `com.memorin.member.*` vs `com.memorin.domain.*.Entity.*` 혼재. 표준 경로 확정.
  - 결정: `____________`
- [ ] **E. 소프트 삭제 규칙** — `deleted_at IS NULL` 필터를 어디서 책임지나 (조회 서비스 공통? 도메인별?).

---

## 4. 각 도메인이 외부에 노출할 경계 API 초안

> "남의 테이블 직접 조회 금지, 아래 서비스 메서드로만 접근" 을 전제로 한 제안. 시그니처는 논의용 초안.

### users 도메인이 제공 (다른 모든 도메인이 소비)
| 메서드(안) | 입력 | 출력 | 용도 |
| --- | --- | --- | --- |
| `existsById` | `UUID userId` | `boolean` | FK 대상 유저 존재 검증 |
| `getUserSummary` | `UUID userId` | `UserSummaryDto`(id, username, displayName, profileImageKey) | 피드·채팅·댓글에 작성자 요약 표시 |
| `getUserSummaries` | `List<UUID>` | `Map<UUID, UserSummaryDto>` | 목록 조회 시 N+1 방지 |

### posts 도메인이 제공
| 메서드(안) | 입력 | 출력 | 용도 |
| --- | --- | --- | --- |
| `existsById` | `UUID postId` | `boolean` | media/like/comment의 FK 검증 |
| `isOwnedBy` | `UUID postId, UUID userId` | `boolean` | 권한 체크 |

### media / infra 도메인이 제공
| 메서드(안) | 입력 | 출력 | 용도 |
| --- | --- | --- | --- |
| `issuePresignedUpload` | `업로드 요청(파일명·타입)` | presigned URL + fileKey | posts가 미디어 첨부 시 |
| `sumUsageBytes` | `UUID userId` | `long` | Quota 집계 (`post_media.deleted_at IS NULL` 대상) |

---

## 5. 확정 후 산출물

- 위 표의 "🔴 합의 필요" 칸이 모두 채워지고,
- 3번 체크리스트 A~E가 결정되면,
- 이 문서를 **`도메인 인터페이스 v1`** 으로 확정(이후 변경은 PM 승인) → 게이트 항목 충족.

> 결정 로그(수요일 채움):
> - A. 유저 엔티티 = …
> - B. 참조 방식 = …
> - C/D/E. …

# 도메인 인터페이스 초안 (Sprint 0 · Week 2 수요일 리뷰용)

> **목적** — Sprint 1 본격 구현에 들어가기 전에, BE 각자가 맡은 도메인이 **서로를 어떻게 호출할지(경계)** 를 미리 합의한다.
> Sprint 0 게이트 체크리스트의 **"BE: 도메인 인터페이스 경계 문서 합의 완료"** 항목을 충족하기 위한 초안이다.
> **상태**: 결정사항 A~D는 **Week 4 수요일 확정**(3절·5절 결정 로그 참고). E(소프트 삭제 공통화)만 후속 논의로 미확정.

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

참조 방식은 **B(옵션1: @ManyToOne 직접 참조)** 로 확정. 연관 필드명은 `_id` 없이 대상 이름으로 통일(Week 4 리팩터 반영).

| 참조하는 쪽(내 도메인) | 참조 대상 | 연결 컬럼(FK, DB) | Java 연관 필드 | 참조 방식 |
| --- | --- | --- | --- | --- |
| posts | users | `posts.user_id` | `Post.user` | @ManyToOne |
| post_media | posts | `post_media.post_id` | `PostMedia.post` | @ManyToOne |
| post_likes | posts, users | `post_id`, `user_id` | `PostLikes.post`, `.user` | @ManyToOne |
| post_comments | posts, users, (self) | `post_id`, `user_id`, `parent_id` | `PostComments.post`, `.user`, `.parent` | @ManyToOne |
| follows | users(×2) | `follower_id`, `following_id` | `Follows.follower`, `.following` | @ManyToOne |
| chat_room_members | chat_rooms, users | `room_id`, `user_id` | `ChatRoomMembers.room`, `.user` | @ManyToOne |
| messages | chat_rooms, users | `room_id`, `sender_id` | `Messages.room`, `.sender` | @ManyToOne |

---

## 3. 핵심 결정사항 (Week 4 수요일 확정)

- [x] **A. 유저 엔티티 단일화** — `member.entity.Member` 는 삭제되었고 `domain.users.entity.User` 로 단일화 완료.
  - **결정: 표준 엔티티 = `com.memorin.domain.users.entity.User` / 삭제할 것 = `member.entity.Member`(삭제 완료)**
- [x] **B. 도메인 간 참조 방식** — **옵션1(JPA 직접 참조)로 확정.** 전 엔티티가 `@ManyToOne(fetch = LAZY)` 로 통일됨.
  - 옵션1: `@ManyToOne User user` (JPA 직접 참조 — 조인 편함, 도메인 결합 강함) ← **채택**
  - 옵션2: `UUID userId` 필드 + 필요 시 `UserQueryService`로 조회 (결합 약함, 조인 직접 관리)
  - **결정: `옵션1 — @ManyToOne 직접 참조`. 연관 필드명은 `_id` 접미사 없이 대상 이름으로(`user`, `post`, `follower`, `sender`).**
- [x] **C. 필드 네이밍 컨벤션** — **Java 필드 = camelCase, `@Column(name=...)`/`@JoinColumn(name=...)` = snake_case(DB 컬럼).** 전 엔티티 적용 완료(Week 4 리팩터).
  ```java
  @Column(name = "created_at")   // DB 컬럼: snake_case
  private LocalDateTime createdAt; // Java 필드: camelCase
  ```
  - 주의: JPQL(`@Query`)의 프로퍼티 경로는 **Java 필드명(camelCase)**, 네이티브 쿼리(`nativeQuery=true`)는 **DB 컬럼명(snake_case)**.
- [x] **D. 패키지 구조** — **전 패키지 소문자 통일**(Week 4 리팩터). `Entity/Controller/Repository/Service/Request/Response` 대문자 세그먼트 제거.
  - **결정: `com.memorin.domain.<도메인>.{controller|service|repository|entity|dto.request|dto.response}` (전부 소문자). `member.*` 패키지 폐기.**
- [ ] **E. 소프트 삭제 규칙** — `deleted_at IS NULL` 필터를 어디서 책임지나 (조회 서비스 공통? 도메인별?). *(미확정 — 조회 레이어 공통화 방안 후속 논의)*

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

> 결정 로그 (Week 4 수요일 확정):
> - A. 유저 엔티티 = `domain.users.entity.User` 단일화 (Member 삭제 완료)
> - B. 참조 방식 = 옵션1 `@ManyToOne` 직접 참조. 연관 필드명 `_id` 접미사 제거
> - C. 네이밍 = Java camelCase / `@Column` snake_case — 전 엔티티 적용 완료
> - D. 패키지 = 전부 소문자(`controller/service/repository/entity/dto`) — `member.*` 폐기
> - E. 소프트 삭제 공통화 = **미확정**, 후속 논의

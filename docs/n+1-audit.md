# N+1 점검 가이드 & 피드 API 점검 기록

> 목적: 목록/피드 API를 만들 때 N+1 쿼리를 예방·검증하는 **팀 공통 기준**과, 그 첫 사례인 게시물 피드 점검 결과를 남긴다.
> 대상: 목록 조회 API(피드/댓글/팔로우/좋아요/채팅 등)를 구현·리뷰하는 모든 백엔드 담당자.

---

## 1. 핵심 원칙

> **N+1은 코드 모양으로 단정하지 말고, 실제로 나간 SQL 개수로 판단한다.**

- 코드만 보면 "명백한 N+1"이 1차 캐시 덕에 실제로는 0번 나가기도 한다. (아래 2절 사례)
- 반대로, 지금 안전한 코드가 트랜잭션 경계 하나 바뀌면 진짜 N+1로 돌변하기도 한다.
- 그래서 **① 실측하고 ② 테스트로 못 박는다.**

---

## 2. 사례 — 피드 API 점검 (예상이 뒤집힌 기록)

### 2-1. 예상: "라이브 N+1이다"

피드 조회 시 미디어마다 다운로드 URL을 발급하는데, 그 로직이 이미 손에 든 `PostMedia`를 **id만 뽑아 `findById`로 재조회**하고 있었다.

```java
// (개선 전) PresignedDownloadService
public PresignedDownloadResponse createDownloadUrl(UUID postMediaId) {
    PostMedia postMedia = postMediaRepository.findById(postMediaId) // ← 손에 든 걸 다시 조회
            .orElseThrow(() -> new PostMediaNotFoundException(postMediaId));
    // ...presigned URL 생성...
}
```

게시물 20개 × 미디어 3장이면 `findById` 60번. 코드상 명백한 N+1로 보였다.

### 2-2. 실측: `findById`가 DB로 **0번** 나갔다

실제 나간 쿼리는 3개뿐.

```sql
SELECT * FROM posts p WHERE ...                                          -- 피드
... FROM post_media pm1_0 ... WHERE p1_0.id IN (?, ?, ?, ?, ?)          -- 미디어 배치
... FROM users u1_0 WHERE u1_0.id = ?                                    -- 인증용 유저
```

### 2-3. 이유: 영속성 컨텍스트(1차 캐시)

피드 조회는 `@Transactional(readOnly = true)`로 **하나의 트랜잭션**이다.

1. 미디어 10개를 **배치 쿼리 1번**으로 읽음 → 10개 전부 1차 캐시에 적재
2. URL 발급이 `findById(같은 id)` 호출 → **캐시에 있으니 DB 안 감** → SQL 0번

즉 "우연히" 안전했던 것. **1차 캐시에 의존하는 불안정한 상태**였다.

### 2-4. 그래서 개선한 것 (라이브 버그는 아니지만 잠재 위험 제거)

- **취약함**: 트랜잭션을 쪼개거나 트랜잭션 밖에서 호출하면 → 진짜 N+1 / `LazyInitializationException`
- **숨은 버그**: `Objects.requireNonNull(resolveDownloadUrl(...))` — `resolveDownloadUrl`은 실패 시 의도적으로 `null`을 반환(미디어 한 건 실패로 게시물 전체가 안 깨지게)하는데, `requireNonNull`이 그 방어를 무력화해 **미디어 한 건 실패 → 게시물 조회 전체 500**

**개선 방향**: `PostMedia`를 통째로 받는 오버로드를 만들어 `findById` 제거 + `requireNonNull` 삭제.

```java
// (개선 후) PresignedDownloadService
// 단건 API용 — id만 아는 경우. 하위호환 유지.
public PresignedDownloadResponse createDownloadUrl(UUID postMediaId) {
    PostMedia postMedia = postMediaRepository.findById(postMediaId)
            .orElseThrow(() -> new PostMediaNotFoundException(postMediaId));
    return createDownloadUrl(postMedia);
}

// 목록용 — 이미 엔티티를 손에 든 경우. DB 재조회 없음.
public PresignedDownloadResponse createDownloadUrl(PostMedia postMedia) { ... }
```

```java
// (개선 후) PostService
private List<PostMediaResponse> toMediaResponses(List<PostMedia> media) {
    return media.stream()
            .map(m -> PostMediaResponse.from(m, resolveDownloadUrl(m))) // id 대신 엔티티
            .toList();
}

private String resolveDownloadUrl(PostMedia media) {
    try {
        return presignedDownloadService.createDownloadUrl(media).downloadUrl();
    } catch (Exception e) {
        return null; // 미디어 한 건 실패가 게시물 조회 전체를 깨뜨리지 않도록
    }
}
```

### 2-5. 결과 (개선 전후 동일 — flat)

| 데이터 | SQL 개수 |
|---|---|
| 게시물 5개 / 미디어 5장 | 2개 |
| 게시물 5개 / 미디어 15장 | 2개 |

미디어를 3배로 늘려도 2개(피드 1 + 미디어 배치 1). **N+1 없음이 테스트로 증명됨.** 리팩터링 후에도 숫자가 그대로 = 회귀 없음.

---

## 3. 점검 방법

### 3-1. 로그로 세기 (수동, 빠른 확인)

`application.properties` (로컬 점검용, **커밋 금지**):

```properties
spring.jpa.show-sql=true
logging.level.org.hibernate.SQL=debug
```

```bash
# 기준 시각 → 딱 1번 호출 → 그 이후 쿼리 개수
SINCE=$(date -u +%Y-%m-%dT%H:%M:%S)
# (여기서 API를 딱 한 번 호출)
docker compose logs backend --since "${SINCE}Z" | grep -c "Hibernate:"
```

> 주의
> - `show-sql`과 `logging.level.org.hibernate.SQL`이 같은 쿼리를 **두 번** 찍는다. `Hibernate:` 줄만 세면 쿼리당 1개.
> - **딱 1번만** 호출한다(여러 번 호출하면 로그가 섞여 못 셈).

### 3-2. 테스트로 못 박기 (자동, 권장) ⭐

`PostFeedQueryCountTest`가 표준 패턴이다. 새 목록 API를 만들면 **이 테스트를 복사**해서 쿼리 수를 고정한다.

- **seed 트랜잭션 ↔ 측정 트랜잭션 분리**(`TransactionTemplate`): 같은 트랜잭션에서 재면 1차 캐시가 조회를 가로채 실제 운영 쿼리가 안 보인다.
- **`Statistics.getPrepareStatementCount()`**: 실제 나간 SQL만 센다(캐시 히트는 제외).
- **데이터를 N배 늘려도 쿼리 수가 같으면 통과**, 비례해 늘면 N+1.

> 위치: `backend/src/test/java/com/memorin/domain/posts/PostFeedQueryCountTest.java`

---

## 4. 목록 API 구현·리뷰 체크리스트

새 목록 API(댓글/팔로우/좋아요/채팅 등)를 만들 때 **반드시** 확인:

- [ ] 연관관계는 전부 `FetchType.LAZY`인가
- [ ] 응답에서 연관 엔티티의 **PK만** 쓰는가?
      - `getUser().getId()` → 프록시, 쿼리 X (안전)
      - `getUser().getNickname()` 등 **다른 필드 접근 → 그 순간 N+1**
- [ ] **1:N**(게시물→미디어, 게시물→댓글)은 `IN` 배치 조회 + `groupingBy`로 묶었는가
- [ ] **N:1**(댓글→작성자, 팔로우→상대 유저)은 `JOIN FETCH`로 한 번에 가져오는가
      ```java
      @Query("SELECT c FROM PostComments c JOIN FETCH c.user WHERE c.post.id = :postId")
      List<PostComments> findByPostIdWithAuthor(@Param("postId") UUID postId);
      ```
- [ ] 보험: `spring.jpa.properties.hibernate.default_batch_fetch_size=100` (근본 해결 아님, 안전망)
- [ ] **`PostFeedQueryCountTest` 패턴의 쿼리 개수 테스트를 추가**했는가

### 예약된 위험 지점 (엔티티는 있으나 API 미구현)

| 엔티티 | 목록 API | 위험한 LAZY 연관 |
|---|---|---|
| `PostComments` | 댓글 목록 | `user`(작성자), `post`, `parent` |
| `Follows` | 팔로워/팔로잉 목록 | follower·following **User 2개** |
| `Messages` | 채팅 페이징 | 발신자, 채팅방 |
| `PostLikes` | 좋아요 누른 사람 | user, post |

특히 **댓글·팔로우 목록**은 작성자/상대 유저의 닉네임·프로필을 응답에 담으므로 N+1 위험이 가장 높다. 구현 시 `JOIN FETCH` 필수.

---

## 5. Sprint 2 점검 — 피드·댓글·친구목록 + 인덱스 (Week 5)

> 대상: Sprint 2에서 머지된 댓글 CRUD(#111)·공개범위 필터링(#104)·친구 목록(#102) 경로.
> 섹션 4의 "예약된 위험 지점" 예측이 실제로 터졌는지 코드로 확인한 결과.

### 5-1. 🔴 확정 N+1 — 친구 목록 조회 (#102)

`UserService.getFollowers()` / `getFollowings()` (`UserService.java:61-102`):

```java
List<Follows> follows = followRepository.findByFollowingIdAndStatus(userId, ACCEPTED); // 페치 조인 없음
for (Follows follow : follows) {
    User user = follow.getFollower();          // LAZY 프록시
    items.add(UserFollowResponse.from(user));  // user.getUsername()/getDisplayName()/getProfileImageKey() 접근
}
```

- `UserFollowResponse.from`이 **PK가 아닌 필드**(`username`·`displayName`·`profileImageKey`)를 접근 → 프록시 초기화 → **팔로워 1명당 SELECT 1번**.
- `UserService`가 `@Transactional`이라 `LazyInitializationException`은 안 나고 **조용히 N+1**(팔로워 200명 → 1+200 쿼리)이 된다. 섹션 4가 "가장 위험"이라 예측한 바로 그 지점.
- **처방**: 리포지토리에 페치 조인 추가.
  ```java
  @Query("SELECT f FROM Follows f JOIN FETCH f.follower WHERE f.following.id = :id AND f.status = :status")
  List<Follows> findFollowersWithUser(@Param("id") UUID id, @Param("status") Follow_state status);
  ```
- 🐛 **덤(N+1과 별개)**: 이 메서드는 `cursor`·`size` 파라미터를 무시하고 **전체를 로드**한 뒤 `items.size() == size + 1`로 hasNext를 판단(항상 false) → 페이지네이션 미동작 + 무한정 로드. → #102 담당(BE주니어1)에게 라우팅.

### 5-2. 🟡 안전하지만 잠재 위험 — 실측 테스트로 고정할 것

| 경로 | 현재 코드 | 판정 |
|---|---|---|
| 댓글 스레드 `PostCommentService.getThread` | `PostCommentResponse.from`이 `getUser().getId()`·`getParent().getId()`만 접근 | ✅ 프록시 PK → DB 안 감. **지금은 N+1 없음** |
| 유저 피드 `PostService.list` | 미디어 `IN` 배치 1쿼리 + `getUser().getId()` | ✅ 안전 |
| 추천 피드 `RecommendedFeedService` | 좋아요/댓글 수 `countAllByPostIdIn` 배치 2쿼리 | ✅ 안전 |

- 댓글 스레드는 **작성자 닉네임/프로필을 응답에 추가하는 순간 N+1로 전환**된다(섹션 4 체크리스트 위반이 됨). 지금 `PostFeedQueryCountTest` 패턴의 **쿼리 카운트 테스트로 못 박아** 회귀를 막을 것.

### 5-3. 🟢 JSONB 인덱싱 결론 — "추가"가 아니라 "제거"

`index.sql`에 `idx_posts_content ON posts USING GIN (content)` 가 있으나, **`content`(jsonb)는 어떤 쿼리의 WHERE/ORDER/JOIN 조건에도 쓰이지 않는다**(PK 조회·저장·응답 반환만). `Messages.content`도 동일.

- GIN 인덱스는 조회 이득이 0인데 **게시물 insert/update마다 유지비용**만 발생하는 **죽은 인덱스**.
- **처방**: `DROP INDEX idx_posts_content;` (실제 jsonb 본문 검색 요구가 생기면 그때 재도입). 현 시점 JSONB 인덱싱 검토의 결론.

### 5-4. 🔵 진짜 필요한 건 B-tree — 인덱스 갭

| 쿼리 | 현재 인덱스 | 문제 / 처방 |
|---|---|---|
| `findRecommendationCandidates`<br>(`deleted_at IS NULL AND visibility='PUBLIC' AND created_at 범위 ORDER BY created_at DESC`) | **없음** | 후보 풀 스캔. → 부분 인덱스 추가:<br>`CREATE INDEX ON posts (created_at DESC) WHERE deleted_at IS NULL AND visibility='PUBLIC';` |
| `findUserFeed` 키셋 페이징<br>(`(recorded_date, id) < (...) ORDER BY recorded_date DESC, id DESC`) | `idx_posts_user_date (user_id, recorded_date DESC)` | tie-break `id` 미포함 → `(user_id, recorded_date DESC, id DESC)`로 확장 검토 |
| 댓글 스레드 `findThreadByPostId`<br>(tombstone 포함, `deleted_at` 필터 없음) | `idx_post_comments_post ... WHERE deleted_at IS NULL` (부분) | 부분 인덱스 조건 불일치 → 스레드 쿼리는 이 인덱스를 못 씀. 대형 스레드 정렬 비용. |

### 5-5. ⚠️ 리뷰 중 발견(검증 필요) — 네이티브 쿼리 오타

`PostRepository.findRecommendationCandidates`의 `AND p.created_at <= : asOf` — **콜론과 파라미터 사이 공백**. Hibernate가 `:asOf` 바인딩을 못 잡아 추천 피드 실행 시 SQL 오류가 날 수 있음. **실측(추천 피드 실제 호출/테스트)으로 확인 필요.**

---

## 6. 한 줄 요약

**측정하지 않은 N+1 주장은 추측이다. 실측하고, 테스트로 고정하라.**

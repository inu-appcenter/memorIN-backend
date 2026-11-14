# memorIN-backend 기여 안내

## 시작하기
- 로컬 실행은 [README의 시작하기](README.md#시작하기-로컬-개발)를 따릅니다.
- 작업은 이슈에서 시작합니다. 이슈 템플릿(버그, 기능, 문서)을 쓰고 담당자를 지정합니다.

## 브랜치
- `develop`에서 브랜치를 만들어 `develop`으로 PR을 올립니다.
- 브랜치 이름은 `<type>/<요약>` 형식입니다. 예: `fix/web-push-crypto-key-padding`
- `main`은 `develop` → `main` 릴리스 PR(제목 `release: ...`)로만 바꿉니다.
- 병합 방식
  - 기능 PR과 릴리스 PR 모두 merge commit("Merge pull request")으로 병합합니다.
  - 릴리스를 squash로 병합하면 `main`과 `develop`의 이력이 갈라지니 쓰지 않습니다.
- 브랜치 보호
  - `main`, `develop`은 직접 푸시, force push, 삭제가 막혀 있습니다.
  - 병합된 브랜치는 자동으로 삭제됩니다.

## 커밋
- 메시지는 첫 줄 `<type>: <설명>`, 그 아래 본문에 왜 바꿨는지를 적습니다.
- 도구가 자동으로 붙이는 공동 작성자 트레일러나 세션 링크는 넣지 않습니다.
  - PR의 "커밋·본문 서명 검사"가 막습니다.
  - 팀원끼리의 `Co-authored-by`는 GitHub noreply 주소(`<ID>+<아이디>@users.noreply.github.com`)나 실제 주소로 적으면 통과합니다.

## PR
- 제목: `<type>: <설명>`
  - type: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `style`, `release`(릴리스 전용)
  - 범위를 붙일 수 있습니다. 예: `chore(deps): ...`
- 본문은 PR 템플릿(요약, 관련 이슈, 변경 사항, 검증, 범위 밖/후속, 리뷰 포인트)을 채웁니다.
  - 이슈를 닫는 PR은 `Closes #번호`를 적습니다.
  - 도구가 자동으로 붙이는 "Generated with" 꼬리말은 지웁니다.
- 리뷰
  - 리뷰어를 1명 이상 지정합니다.
  - 현재 병합에 필요한 승인 수는 0명입니다. <!-- 결정 필요: 승인 필수 인원 -->
  - 변경 요청(Changes requested) 리뷰가 남아 있으면 병합이 막힙니다. 반영 후 리뷰어가 다시 승인합니다.
- PR에서 도는 검사
  - 빌드 및 테스트
  - Flyway 마이그레이션 검사
  - 이미지 빌드 검증
  - PR 제목 컨벤션 검사
  - 커밋·본문 서명 검사
  - Qodana, CodeQL

## 테스트
- `./gradlew test`
  - Testcontainers를 쓰므로 Docker가 필요합니다.
  - `@Tag("stress")` 스트레스 테스트는 빠지며, `./gradlew stressTest`로 따로 돌립니다.
- 인증이 필요한 새 엔드포인트는 컨트롤러를 지나가는 테스트(MockMvc)를 1개 이상 추가합니다. 예: `PostErrorResponseTest` <!-- 결정 필요(#204): 팀 규칙으로 둘지 -->

## DB 마이그레이션
- 이미 있는 `V*.sql`은 고치거나 지우지 않습니다. 변경은 새 버전 파일로 추가합니다.
  - 기존 파일을 바꾸면 운영 DB가 체크섬 불일치로 기동하지 않습니다.
- 버전 번호가 겹치면 CI가 실패합니다. 다른 PR과 번호가 겹치지 않는지 병합 전에 확인합니다.
- 자세한 절차는 [`docs/db-migration-guide.md`](docs/db-migration-guide.md)를 봅니다.

## 의존성
- Dependabot이 `develop` 대상으로 묶음 PR을 올립니다.
  - Gradle과 Docker는 매주 월요일 09:00(KST), GitHub Actions는 매월 올라옵니다.
  - 메이저 업데이트는 받지 않습니다.
- 묶음 PR은 CI를 확인한 뒤 병합합니다.
- `nl.martijndwars:web-push` 5.1.2는 FCM이 거절하는 헤더를 만들어 무시하도록 설정돼 있습니다.
  - 올릴 때는 `WebPushRequestHeaderTest`로 먼저 확인합니다.

## 비밀값
- 비밀값은 `.env`와 `secrets/`에서만 다루고 커밋하지 않습니다.
  - 알려진 형식의 토큰·키는 push 단계에서 막히지만, 직접 만든 비밀번호 같은 값은 걸러지지 않습니다.
- 보안 문제 제보는 [SECURITY.md](SECURITY.md)를 봅니다.

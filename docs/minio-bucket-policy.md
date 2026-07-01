# MinIO 버킷 정책 초안

## 1. 현재 확인 상태

- MinIO 컨테이너 실행 확인
- MinIO Console 접속 확인: `http://localhost:9001`
- Bucket 생성 확인: `memorin-media`
- Bucket Access: `PRIVATE`
- 테스트 파일 업로드 확인: `test.txt`

## 2. Bucket 정책

- Bucket Name: `memorin-media`
- Access Policy: `Private`
- Anonymous/Public Access: `None`

사용자 업로드 파일은 외부에 직접 노출하지 않고, 기본적으로 Private Bucket에 저장한다.

## 3. 접근 방식

클라이언트는 MinIO에 직접 접근하지 않는다.

업로드/다운로드는 백엔드에서 사용자 인증 및 권한을 검증한 뒤 Presigned URL을 발급하는 방식으로 처리한다.

## 4. Quota 관리 방향

사용자별 Quota는 MinIO bucket quota가 아니라 백엔드 DB 기반으로 관리하는 방향을 제안한다.

이유:
- `memorin-media` 단일 버킷에 여러 사용자의 파일을 저장할 예정
- MinIO bucket quota는 사용자별 제한보다 버킷 전체 제한에 적합
- 서비스 사용자별 사용량은 백엔드가 DB에서 관리하는 것이 적절

업로드 전 백엔드에서 다음 조건을 검사한다.

```text
현재 사용량 + 업로드 예정 파일 크기 <= 사용자 저장 용량 제한
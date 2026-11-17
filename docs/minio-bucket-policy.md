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

클라이언트는 스토리지 자격 증명을 갖지 않고, 백엔드가 인증과 권한 검증 뒤 발급한 presigned URL로만 오브젝트에 직접 접근한다.

업로드는 presigned PUT, 다운로드는 presigned GET으로 클라이언트가 스토리지 S3 API에 직접 요청한다. 그래서 S3 API 주소(`MINIO_PUBLIC_ENDPOINT`)는 클라이언트가 닿을 수 있어야 하고, 관리 콘솔(9001)은 외부에 열지 않는다. 운영에서 이 주소를 둘 때의 조건은 `docs/api-spec.md` 5-2절을 따른다.

## 4. Quota 관리 방향

사용자별 Quota는 MinIO bucket quota가 아니라 백엔드 DB 기반으로 관리한다(`memorin-media` 단일 버킷에 여러 사용자의 파일을 저장하므로, 버킷 전체 제한보다 사용자별 제한에 DB 기반 관리가 적합).

세부 산정 기준·검증 시점·동시성 보장은 `docs/storage-quota-policy.md`를 기준으로 한다.
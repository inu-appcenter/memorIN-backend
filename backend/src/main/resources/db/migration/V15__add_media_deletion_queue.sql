-- media_deletion_queue: 첨부 교체(PostService.update)로 post_media 행이 하드 삭제될 때,
-- 버려진 MinIO 오브젝트의 file_key를 남겨 두는 대기열 (이슈 #259).
-- 행이 사라지면 어떤 오브젝트가 고아가 됐는지 DB에 흔적이 없어 나중에 정리할 수 없다.
-- 정리 배치(DeletedMediaCleanupJob)가 유예 기간 후 MinIO 오브젝트를 지우고 이 행도 지운다.
CREATE TABLE IF NOT EXISTS media_deletion_queue (
    id         UUID         PRIMARY KEY DEFAULT uuidv7(),
    file_key   VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_media_deletion_queue_created_at ON media_deletion_queue (created_at);

-- 정리 배치가 "같은 file_key를 아직 살아 있는 게시물이 쓰는가"를 오브젝트마다 확인한다.
CREATE INDEX IF NOT EXISTS idx_post_media_file_key ON post_media (file_key);

-- 소프트 삭제된 게시물을 유예 기간 기준으로 찾는다. 기존 부분 인덱스는 전부 deleted_at IS NULL 쪽이라 못 탄다.
CREATE INDEX IF NOT EXISTS idx_posts_deleted_at ON posts (deleted_at) WHERE deleted_at IS NOT NULL;

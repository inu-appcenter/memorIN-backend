package com.memorin.global.media.service;

import com.memorin.domain.post_media.entity.PostMedia;
import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.domain.posts.entity.Post;
import com.memorin.domain.posts.entity.TimeslotType;
import com.memorin.domain.posts.entity.VisibilityType;
import com.memorin.domain.users.entity.User;
import com.memorin.global.media.MinioProperties;
import com.memorin.global.media.dto.response.PresignedDownloadResponse;
import com.memorin.global.media.exception.MediaAccessDeniedException;
import com.memorin.global.media.exception.PostMediaNotFoundException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// IDOR 방지 검증: 이슈 #70 S3 - 소유자/공개범위 확인 없이 임의 postMediaId로
// presigned GET URL이 발급되던 문제를 막는지 실제 Postgres 스키마 기준으로 검증한다.
//
// PostgresTestSupport를 상속하지 않고 컨테이너를 직접 관리한다.
// 그 클래스의 static 컨테이너 필드는 상속하는 테스트 클래스마다 JUnit5 Testcontainers 확장이
// beforeAll/afterAll을 걸어, 같은 인스턴스인데도 먼저 끝난 클래스의 afterAll이 컨테이너를 stop시켜
// 나중에 도는 클래스가 죽은 포트에 연결을 시도하는 문제가 있다 (전체 스위트 실행 시 재현 확인).
// 여기서는 한 번만 start하고 절대 stop하지 않는 독립 컨테이너를 써서 그 문제를 피한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PresignedDownloadServiceTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../infra/postgres/init/01_init.sql"),
                    "/docker-entrypoint-initdb.d/01_init.sql"
            );

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // 스키마는 위 init 스크립트가 이미 만들었으므로 hibernate는 손대지 않는다.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PostMediaRepository postMediaRepository;

    private PresignedDownloadService service() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        given(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .willReturn("http://minio.local/signed-url");
        MinioProperties properties = new MinioProperties(
                "http://minio:9000", "http://localhost:9000", "us-east-1",
                "key", "secret", "bucket", 600, 300, 1024, List.of("image/png")
        );
        return new PresignedDownloadService(minioClient, properties, postMediaRepository, Clock.systemUTC());
    }

    private User persistUser(String suffix) {
        return em.persist(new User(
                "tester" + suffix + "@memorin.test", "hashed-password", "tester" + suffix, "테스터" + suffix, null
        ));
    }

    private Post persistPost(User author, VisibilityType visibility) {
        return em.persist(Post.create(
                author, "[]", visibility, TimeslotType.values()[0], Date.valueOf(LocalDate.of(2026, 7, 20))
        ));
    }

    private PostMedia persistMedia(Post post) {
        return em.persist(PostMedia.of(post, "key", "image/png", 100L, (short) 0, 10, 10));
    }

    @Test
    void 소유자는_PRIVATE_미디어의_다운로드_URL을_발급받을_수_있다() throws Exception {
        User owner = persistUser("1");
        Post post = persistPost(owner, VisibilityType.PRIVATE);
        PostMedia media = persistMedia(post);
        em.flush();
        em.clear();

        PresignedDownloadResponse response = service().createDownloadUrl(media.getId(), owner.getId());

        assertThat(response.downloadUrl()).isNotBlank();
    }

    @Test
    void 소유자가_아니면_PRIVATE_미디어_접근이_거부된다() throws Exception {
        User owner = persistUser("2");
        User stranger = persistUser("3");
        Post post = persistPost(owner, VisibilityType.PRIVATE);
        PostMedia media = persistMedia(post);
        em.flush();
        em.clear();

        PresignedDownloadService service = service();
        assertThatThrownBy(() -> service.createDownloadUrl(media.getId(), stranger.getId()))
                .isInstanceOf(MediaAccessDeniedException.class);
    }

    @Test
    void PUBLIC_미디어는_소유자가_아니어도_접근할_수_있다() throws Exception {
        User owner = persistUser("4");
        User stranger = persistUser("5");
        Post post = persistPost(owner, VisibilityType.PUBLIC);
        PostMedia media = persistMedia(post);
        em.flush();
        em.clear();

        PresignedDownloadResponse response = service().createDownloadUrl(media.getId(), stranger.getId());

        assertThat(response.downloadUrl()).isNotBlank();
    }

    @Test
    void 존재하지_않는_미디어는_NotFound를_던진다() throws Exception {
        PresignedDownloadService service = service();
        assertThatThrownBy(() -> service.createDownloadUrl(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(PostMediaNotFoundException.class);
    }
}

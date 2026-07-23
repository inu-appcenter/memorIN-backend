package com.memorin.global.media.service;

import com.memorin.domain.pending_upload.entity.PendingUpload;
import com.memorin.domain.pending_upload.repository.PendingUploadRepository;
import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.media.StorageQuotaProperties;
import com.memorin.global.media.dto.response.QuotaResponse;
import com.memorin.global.media.exception.StorageQuotaExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StorageQuotaServiceTest {

    @Mock
    private PostMediaRepository postMediaRepository;

    @Mock
    private PendingUploadRepository pendingUploadRepository;

    @Mock
    private UserRepository userRepository;

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);

    private StorageQuotaService service(StorageQuotaProperties properties) {
        return new StorageQuotaService(postMediaRepository, pendingUploadRepository, userRepository, properties, FIXED_CLOCK);
    }

    @Test
    void getQuotaStatus_committed와_pending을_합산한다() {
        // given
        StorageQuotaProperties properties = new StorageQuotaProperties(1_000L, 900L);
        StorageQuotaService service = service(properties);
        UUID userId = UUID.randomUUID();
        given(postMediaRepository.sumFileSizeBytesByUserId(userId)).willReturn(300L);
        given(pendingUploadRepository.sumReservedBytesByUserId(any(), any())).willReturn(100L);

        // when
        QuotaResponse response = service.getQuotaStatus(userId);

        // then
        assertThat(response.usedBytes()).isEqualTo(400L);
        assertThat(response.limitBytes()).isEqualTo(1_000L);
        assertThat(response.remainingBytes()).isEqualTo(600L);
        assertThat(response.usagePercentage()).isCloseTo(40.0, within(0.01));
    }

    @Test
    void getQuotaStatus_사용량이_한도를_초과해도_잔여량은_음수가_되지_않는다() {
        // given
        StorageQuotaProperties properties = new StorageQuotaProperties(1_000L, 900L);
        StorageQuotaService service = service(properties);
        UUID userId = UUID.randomUUID();
        given(postMediaRepository.sumFileSizeBytesByUserId(userId)).willReturn(1_500L);

        // when
        QuotaResponse response = service.getQuotaStatus(userId);

        // then
        assertThat(response.remainingBytes()).isZero();
        assertThat(response.usagePercentage()).isCloseTo(150.0, within(0.01));
    }

    @Test
    void reserveUpload_한도_이내면_pending을_저장한다() {
        // given
        StorageQuotaProperties properties = new StorageQuotaProperties(1_000L, 900L);
        StorageQuotaService service = service(properties);
        UUID userId = UUID.randomUUID();
        given(userRepository.findByIdForUpdate(userId)).willReturn(java.util.Optional.of(mock(User.class)));
        given(postMediaRepository.sumFileSizeBytesByUserId(userId)).willReturn(300L);
        given(pendingUploadRepository.sumReservedBytesByUserId(any(), any())).willReturn(0L);
        given(pendingUploadRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // when
        service.reserveUpload(userId, "uploads/2026/07/22/key.png", 500L);

        // then
        verify(pendingUploadRepository).save(any(PendingUpload.class));
    }

    @Test
    void reserveUpload_committed와_pending_합이_한도를_넘으면_예외를_던진다() {
        // given: committed 300 + 기존 pending 400 + 새 요청 400 = 1100 > 1000
        StorageQuotaProperties properties = new StorageQuotaProperties(1_000L, 900L);
        StorageQuotaService service = service(properties);
        UUID userId = UUID.randomUUID();
        given(userRepository.findByIdForUpdate(userId)).willReturn(java.util.Optional.of(mock(User.class)));
        given(postMediaRepository.sumFileSizeBytesByUserId(userId)).willReturn(300L);
        given(pendingUploadRepository.sumReservedBytesByUserId(any(), any())).willReturn(400L);

        // when // then
        assertThatThrownBy(() -> service.reserveUpload(userId, "uploads/2026/07/22/key.png", 400L))
                .isInstanceOf(StorageQuotaExceededException.class);
    }
}

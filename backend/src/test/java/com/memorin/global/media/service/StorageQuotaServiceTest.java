package com.memorin.global.media.service;

import com.memorin.domain.post_media.repository.PostMediaRepository;
import com.memorin.global.media.StorageQuotaProperties;
import com.memorin.global.media.dto.response.QuotaResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StorageQuotaServiceTest {

    @Mock
    private PostMediaRepository postMediaRepository;

    @Test
    void getQuotaStatus_사용량과_잔여량_사용률을_계산한다() {
        // given
        StorageQuotaProperties properties = new StorageQuotaProperties(1_000L, 500L, 900L);
        StorageQuotaService service = new StorageQuotaService(postMediaRepository, properties);
        UUID userId = UUID.randomUUID();
        given(postMediaRepository.sumFileSizeBytesByUserId(userId)).willReturn(300L);

        // when
        QuotaResponse response = service.getQuotaStatus(userId);

        // then
        assertThat(response.usedBytes()).isEqualTo(300L);
        assertThat(response.limitBytes()).isEqualTo(1_000L);
        assertThat(response.remainingBytes()).isEqualTo(700L);
        assertThat(response.usagePercentage()).isCloseTo(30.0, within(0.01));
    }

    @Test
    void getQuotaStatus_사용량이_한도를_초과해도_잔여량은_음수가_되지_않는다() {
        // given
        StorageQuotaProperties properties = new StorageQuotaProperties(1_000L, 500L, 900L);
        StorageQuotaService service = new StorageQuotaService(postMediaRepository, properties);
        UUID userId = UUID.randomUUID();
        given(postMediaRepository.sumFileSizeBytesByUserId(userId)).willReturn(1_500L);

        // when
        QuotaResponse response = service.getQuotaStatus(userId);

        // then
        assertThat(response.remainingBytes()).isZero();
        assertThat(response.usagePercentage()).isCloseTo(150.0, within(0.01));
    }
}

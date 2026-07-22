package com.memorin.global.media;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 단일 업로드 상한은 MinioProperties.maxUploadSizeBytes 하나로 일원화한다.
// (이전엔 이 레코드에 maxSingleUploadBytes가 따로 있었지만 어디서도 읽지 않는 죽은 설정값이었다 -
//  운영자가 이 값을 바꿔도 실제 업로드 검증엔 반영되지 않는 문제가 있었다.)
@Validated
@ConfigurationProperties(prefix = "storage.quota")
public record StorageQuotaProperties(
        @Min(1) long defaultLimitBytes,
        @Min(1) long pendingTtlSeconds
) {
}

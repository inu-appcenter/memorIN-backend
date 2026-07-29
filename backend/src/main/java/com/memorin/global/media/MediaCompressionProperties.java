package com.memorin.global.media;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 서버는 이미지 바이트를 직접 만지지 않는다(presigned PUT으로 클라이언트가 MinIO에 직접 업로드).
// 이 값들은 클라이언트(앱/웹)가 업로드 전 압축할 때 참고할 가이드 파라미터일 뿐, 서버가 강제하는 값이 아니다.
@Validated
@ConfigurationProperties(prefix = "media.compression")
public record MediaCompressionProperties(
        @Min(1) @Max(100) int imageQualityPercent,
        @Min(1) int imageMaxWidthPx,
        @Min(1) int imageMaxHeightPx
) {
}

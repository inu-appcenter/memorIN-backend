package com.memorin.global.media.exception;

import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;

// 첨부로 커밋하려는 fileKey에 대응하는 pending_uploads 예약이 없거나(다른 사람이 발급받은 키,
// 이미 커밋됨, 애초에 presigned-upload-url을 거치지 않은 키), 만료됐거나, 요청자 소유가 아닐 때.
public class UploadReservationInvalidException extends BusinessException {

    public UploadReservationInvalidException(String objectKey) {
        super(ErrorCode.MEDIA_007, "Invalid or expired upload reservation: " + objectKey);
    }
}

package com.memorin.global.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// @ValidJson 검증 로직. 값을 실제로 파싱해보고 실패하면 검증 실패로 처리한다.
// ObjectMapper는 읽기 전용 파싱에 한해 thread-safe하므로 인스턴스를 공유한다.
public class ValidJsonValidator implements ConstraintValidator<ValidJson, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // 값 존재 여부는 @NotBlank 등 다른 제약이 담당
        }
        try {
            OBJECT_MAPPER.readTree(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

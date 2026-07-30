package com.memorin.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

// 문자열 값이 올바른 JSON 형식인지 검증한다.
// posts.content처럼 DB가 json/jsonb 타입인 컬럼에 매핑되는 필드에 사용한다.
// null은 통과시킨다(값 존재 여부는 @NotBlank 등 다른 제약이 담당).
@Documented
@Constraint(validatedBy = ValidJsonValidator.class)
@Target({FIELD, METHOD, PARAMETER, CONSTRUCTOR, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface ValidJson {

    String message() default "올바른 JSON 형식이 아닙니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

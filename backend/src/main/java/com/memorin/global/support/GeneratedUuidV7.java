package com.memorin.global.support;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PK 필드에 붙이면 저장 시 UUID v7 값이 자동으로 채워진다.
 *
 * <pre>{@code
 * @Id
 * @GeneratedUuidV7
 * @Column(columnDefinition = "uuid", updatable = false, nullable = false)
 * private UUID id;
 * }</pre>
 *
 * @see UuidV7Generator
 */
@IdGeneratorType(UuidV7Generator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface GeneratedUuidV7 {
}

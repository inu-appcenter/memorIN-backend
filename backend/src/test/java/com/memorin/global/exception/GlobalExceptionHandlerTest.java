package com.memorin.global.exception;

import com.memorin.global.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void uniqueViolation_returns409_withoutExposingCauseMessage() {
        var response = handler.handleDataIntegrityViolation(violation("23505", "Key (email)=(a@b.c) already exists."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.COMMON_006.name());
    }

    @Test
    void otherConstraintViolation_returns400() {
        var response = handler.handleDataIntegrityViolation(violation("22001", "value too long for type character varying(500)"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.COMMON_002.name());
    }

    private static DataIntegrityViolationException violation(String sqlState, String detail) {
        return new DataIntegrityViolationException(detail, new RuntimeException(new SQLException(detail, sqlState)));
    }
}

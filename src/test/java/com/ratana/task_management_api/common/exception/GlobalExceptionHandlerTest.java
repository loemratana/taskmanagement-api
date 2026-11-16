package com.ratana.task_management_api.common.exception;

import com.ratana.task_management_api.task.exception.TaskStateConflictException;

import com.ratana.task_management_api.common.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** DEF-22: framework errors no longer become 500, and every error carries an errorCode. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/tasks/x/status");

    @Test
    @DisplayName("TC-TASK-03: malformed JSON / bad enum → 400 MALFORMED_REQUEST")
    void unreadableBody_is400() {
        ResponseEntity<ErrorResponse> response = handler.handleUnreadable(
                new HttpMessageNotReadableException("bad", mock(HttpInputMessage.class)), request);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getErrorCode()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    @DisplayName("TC-GEN-01: non-UUID path variable → 400 INVALID_PARAMETER")
    void typeMismatch_is400() {
        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(new MethodArgumentTypeMismatchException(
                "abc", UUID.class, "id", mock(MethodParameter.class), null), request);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INVALID_PARAMETER");
    }

    @Test
    @DisplayName("duplicate/constraint violation → 409 DATA_CONFLICT without leaking constraint names")
    void dataIntegrity_is409() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("uq_task_submission_round"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getErrorCode()).isEqualTo("DATA_CONFLICT");
        assertThat(response.getBody().getMessage()).doesNotContain("uq_task_submission_round");
    }

    @Test
    @DisplayName("task state conflicts → 409 with their own code")
    void taskStateConflict_is409() {
        ResponseEntity<ErrorResponse> response = handler.handleTaskStateConflict(new TaskStateConflictException(
                TaskStateConflictException.USE_SUBMISSION_ENDPOINT, "use submissions"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getErrorCode()).isEqualTo("USE_SUBMISSION_ENDPOINT");
    }
}

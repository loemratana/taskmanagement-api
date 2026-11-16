package com.ratana.task_management_api.common.exception;

import com.ratana.task_management_api.auth.exception.InvalidPasswordException;
import com.ratana.task_management_api.auth.exception.TokenRefreshException;
import com.ratana.task_management_api.auth.exception.TooManyAttemptsException;
import com.ratana.task_management_api.booking.exception.BookingConflictException;
import com.ratana.task_management_api.booking.exception.BookingScopeException;
import com.ratana.task_management_api.booking.exception.BookingStateException;
import com.ratana.task_management_api.booking.exception.RoomCapacityExceededException;
import com.ratana.task_management_api.file.exception.AttachmentNotFoundException;
import com.ratana.task_management_api.file.exception.FileSizeExceededException;
import com.ratana.task_management_api.file.exception.UnsupportedFileTypeException;
import com.ratana.task_management_api.infrastructure.messaging.TelegramApiException;
import com.ratana.task_management_api.infrastructure.storage.InvalidDirectoryException;
import com.ratana.task_management_api.infrastructure.storage.StorageException;
import com.ratana.task_management_api.task.exception.InvalidAssignmentException;
import com.ratana.task_management_api.task.exception.InvalidTaskTransitionException;
import com.ratana.task_management_api.task.exception.TaskNotFoundException;
import com.ratana.task_management_api.task.exception.TaskPermissionException;
import com.ratana.task_management_api.task.exception.TaskStateConflictException;
import com.ratana.task_management_api.task.exception.TaskTransitionException;
import com.ratana.task_management_api.task.exception.UnauthorizedTaskAccessException;
import com.ratana.task_management_api.task.exception.UnauthorizedTaskTransitionException;
import com.ratana.task_management_api.user.exception.DuplicateEmailException;

import com.ratana.task_management_api.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Every error carries a stable {@code errorCode} (see docs/system-design/08-api-design.md §4).
 * HTTP statuses are unchanged from before except where a framework exception used to fall
 * through to 500.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ---------------------------------------------------------------- 400 — request shape

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ErrorResponse> handleValidation(BindException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        ErrorResponse body = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message, request.getRequestURI(), fieldErrors.isEmpty() ? null : fieldErrors);
        body.setErrorCode("VALIDATION_FAILED");
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is malformed or contains an invalid value.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Invalid value for parameter {}: {}", ex.getName(), ex.getValue());
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Invalid value for parameter '%s'.".formatted(ex.getName()), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", ex.getMessage(), request);
    }

    @ExceptionHandler({
            InvalidTaskTransitionException.class,
            TaskTransitionException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidTransition(RuntimeException ex, HttpServletRequest request) {
        log.warn("Invalid request: {}", ex.getMessage());
        String code = ex instanceof IllegalArgumentException ? "INVALID_REQUEST" : "INVALID_STATUS_TRANSITION";
        return build(HttpStatus.BAD_REQUEST, code, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler({InvalidRequestException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception ex, HttpServletRequest request) {
        log.warn("Invalid request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFile(UnsupportedFileTypeException ex, HttpServletRequest request) {
        log.warn("Rejected upload: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidDirectoryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDirectory(InvalidDirectoryException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_DIRECTORY", ex.getMessage(), request);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException ex, HttpServletRequest request) {
        log.warn("Malformed multipart request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Malformed file upload. Send the file as multipart/form-data.", request);
    }

    // ---------------------------------------------------------------- 401 / 403

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "Invalid email or password", request);
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyAttempts(TooManyAttemptsException ex, HttpServletRequest request) {
        log.warn("Rate limit hit on {}: {}", request.getRequestURI(), ex.getMessage());
        ResponseEntity<ErrorResponse> response = build(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", ex.getMessage(), request);
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(response.getBody());
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<ErrorResponse> handleTokenRefresh(TokenRefreshException ex, HttpServletRequest request) {
        log.warn("Token refresh failed: {}", ex.getMessage());
        // Kept at 403: both clients sign out on any refresh failure; 401 would make them try to refresh again.
        return build(HttpStatus.FORBIDDEN, "AUTH_REFRESH_INVALID", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        // Method and path: "Access Denied" alone doesn't say which endpoint refused the user.
        log.warn("Access denied: {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action", request);
    }

    @ExceptionHandler({
            UnauthorizedTaskAccessException.class,
            UnauthorizedTaskTransitionException.class,
            TaskPermissionException.class
    })
    public ResponseEntity<ErrorResponse> handleTaskUnauthorized(RuntimeException ex, HttpServletRequest request) {
        log.warn("Task authorization denied: {}", ex.getMessage());
        String code = ex instanceof UnauthorizedTaskTransitionException ? "TASK_TRANSITION_FORBIDDEN" : "TASK_ACCESS_DENIED";
        return build(HttpStatus.FORBIDDEN, code, ex.getMessage(), request);
    }

    @ExceptionHandler(BookingScopeException.class)
    public ResponseEntity<ErrorResponse> handleBookingScope(BookingScopeException ex, HttpServletRequest request) {
        log.warn("Booking scope denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "BOOKING_SCOPE_DENIED", ex.getMessage(), request);
    }

    // ---------------------------------------------------------------- 404 / 405

    @ExceptionHandler({ResourceNotFoundException.class, TaskNotFoundException.class, AttachmentNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The requested resource was not found", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), request);
    }

    // ---------------------------------------------------------------- 409 / 413 / 422

    @ExceptionHandler(TaskStateConflictException.class)
    public ResponseEntity<ErrorResponse> handleTaskStateConflict(TaskStateConflictException ex, HttpServletRequest request) {
        log.warn("Task state conflict ({}): {}", ex.getErrorCode(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<ErrorResponse> handleBookingConflict(BookingConflictException ex, HttpServletRequest request) {
        log.warn("Room booking conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "ROOM_ALREADY_BOOKED", ex.getMessage(), request);
    }

    @ExceptionHandler(BookingStateException.class)
    public ResponseEntity<ErrorResponse> handleBookingState(BookingStateException ex, HttpServletRequest request) {
        log.warn("Booking state conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "BOOKING_NOT_EDITABLE", ex.getMessage(), request);
    }

    @ExceptionHandler(RoomCapacityExceededException.class)
    public ResponseEntity<ErrorResponse> handleRoomCapacity(RoomCapacityExceededException ex, HttpServletRequest request) {
        log.warn("Room capacity exceeded: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "ROOM_CAPACITY_EXCEEDED", ex.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Concurrent modification conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "This record was modified by someone else. Please reload and try again.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // Constraint details can leak schema names; log them, return a generic message.
        log.warn("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The request conflicts with existing data (for example a duplicate). Please reload and try again.", request);
    }

    @ExceptionHandler({DuplicateResourceException.class, DuplicateEmailException.class})
    public ResponseEntity<ErrorResponse> handleDuplicate(RuntimeException ex, HttpServletRequest request) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage(), request);
    }

    @ExceptionHandler({FileSizeExceededException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> handleFileTooLarge(Exception ex, HttpServletRequest request) {
        log.warn("Upload too large: {}", ex.getMessage());
        String message = ex instanceof FileSizeExceededException
                ? ex.getMessage()
                : "Upload is too large. Each file may be at most 20MB and a request at most 100MB.";
        return build(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", message, request);
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handlePasswordException(InvalidPasswordException ex, HttpServletRequest request) {
        log.warn("Invalid password: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "AUTH_PASSWORD_INCORRECT", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidAssignmentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAssignment(InvalidAssignmentException ex, HttpServletRequest request) {
        log.warn("Invalid assignment: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ASSIGNMENT", ex.getMessage(), request);
    }

    // ---------------------------------------------------------------- 5xx

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(StorageException ex, HttpServletRequest request) {
        log.warn("Storage error: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE", ex.getMessage(), request);
    }

    @ExceptionHandler(TelegramApiException.class)
    public ResponseEntity<ErrorResponse> handleTelegramApiException(TelegramApiException ex, HttpServletRequest request) {
        log.warn("Telegram API call failed: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "TELEGRAM_UNAVAILABLE", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: ", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String errorCode, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        body.setErrorCode(errorCode);
        return ResponseEntity.status(status).body(body);
    }
}

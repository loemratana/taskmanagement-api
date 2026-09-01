package com.ratana.task_management_api.dto.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.validation.FieldError;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    boolean success;
    int status;
    String error;
    String message;
    String path;
    Instant timestamp;
    List<FieldError> fieldErrors;


    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
    }
    public static ErrorResponse of(
            int status,
            String error,
            String message,
            String path
    )
    {
        return ErrorResponse.builder()
                .success(false)
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .build();

    }
    public static ErrorResponse of(
            int status,
            String error,
            String message,
            String path,
            List<FieldError> fieldErrors
    ) {
        return ErrorResponse.builder()
                .success(false)
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .fieldErrors(fieldErrors)
                .build();
    }



}

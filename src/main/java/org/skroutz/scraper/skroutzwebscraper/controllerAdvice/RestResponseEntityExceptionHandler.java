package org.skroutz.scraper.skroutzwebscraper.controllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.skroutz.scraper.skroutzwebscraper.utils.ServletUtils;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

@Slf4j
@ControllerAdvice
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAnyOtherException(Exception ex, HttpServletRequest request) {
        return logAndGetApiError(ex, request, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<ApiError> handleLeakedClientErrors(HttpStatusCodeException ex, HttpServletRequest request) {
        return logAndGetApiError(ex, request, ex.getStatusCode(), ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        return logAndGetApiError(ex, request, ex.getStatusCode(), StringUtils.defaultIfBlank(ex.getReason(), ex.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        String[] errors = ex.getConstraintViolations().stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(violation -> getLast(violation.getPropertyPath().iterator()) + ": " + violation.getMessage())
                .toArray(String[]::new);

        return logAndGetApiError(ex, request, HttpStatus.BAD_REQUEST, errors);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                   HttpHeaders headers,
                                                                   HttpStatusCode status,
                                                                   WebRequest request) {
        String[] errors = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toArray(String[]::new);

        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        logException(ex);
        ApiError apiError = ApiError.builder()
                .traceId(getTraceId())
                .status(status.value())
                .errors(Arrays.asList(errors))
                .path(ServletUtils.getPath(servletRequest))
                .method(servletRequest.getMethod())
                .build();

        return new ResponseEntity<>(apiError, status);
    }

    private ResponseEntity<ApiError> logAndGetApiError(Exception ex, HttpServletRequest request, HttpStatusCode status, String... errors) {
        logException(ex);
        return new ResponseEntity<>(ApiError.builder()
                .traceId(getTraceId())
                .status(status.value())
                .errors(Arrays.asList(errors))
                .path(ServletUtils.getPath(request))
                .method(request.getMethod())
                .build(), status);
    }

    private <T> T getLast(Iterator<T> iterator) {
        T value = null;
        while (iterator.hasNext()) {
            value = iterator.next();
        }
        return value;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ApiError {
        private Integer status;
        private String code;
        private String message;
        private List<String> errors;
        private String path;
        private String method;
        private String traceId;
    }

    private void logException(Exception e) {
        log.error("Error leaked controller layer", e);
    }

    private static String getTraceId() {
        return MDC.get("traceId");
    }
}


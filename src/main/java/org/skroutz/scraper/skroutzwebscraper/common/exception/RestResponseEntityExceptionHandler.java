package org.skroutz.scraper.skroutzwebscraper.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.skroutz.scraper.skroutzwebscraper.category.infrastructure.exception.CategorySchemaNotFoundException;
import org.skroutz.scraper.skroutzwebscraper.category.infrastructure.exception.DuplicateCategoryException;
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.exception.ProductNotFoundException;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.exception.JobAlreadyRunningException;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.exception.JobNotFoundException;
import org.skroutz.scraper.skroutzwebscraper.common.utils.ServletUtils;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
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
import java.util.stream.Stream;

@Slf4j
@ControllerAdvice
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAnyOtherException(Exception ex, HttpServletRequest request) {
        return logAndGetApiError(ex, request, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleProductNotFoundException(ProductNotFoundException ex, HttpServletRequest request) {
        return logAndGetApiError(ex, request, HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CategorySchemaNotFoundException.class)
    public ResponseEntity<ApiError> handleCategorySchemaNotFoundException(CategorySchemaNotFoundException ex, HttpServletRequest request) {
        return logAndGetApiError(ex, request, HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateCategoryException.class)
    public ResponseEntity<ApiError> handleDuplicateCategoryException(DuplicateCategoryException ex, HttpServletRequest request) {
        return logAndGetApiError(ex, request, HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(JobAlreadyRunningException.class)
    public ResponseEntity<ApiError> handleJobAlreadyRunningException(JobAlreadyRunningException ex, HttpServletRequest request) {
        return logAndGetApiError(ex, request, HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ApiError> handleJobNotFoundException(JobNotFoundException ex, HttpServletRequest request) {
        return logAndGetApiError(ex, request, HttpStatus.NOT_FOUND, ex.getMessage());
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
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        List<String> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .sorted(Comparator.comparing(ObjectError::getObjectName))
                .map(error -> error.getObjectName() + ": " + error.getDefaultMessage())
                .toList();
        String[] errors = Stream.concat(fieldErrors.stream(), globalErrors.stream()).toArray(String[]::new);

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


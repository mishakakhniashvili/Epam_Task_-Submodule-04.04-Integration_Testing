package com.epam.gymcrm.handler;

import com.epam.gymcrm.dto.ErrorResponse;
import com.epam.gymcrm.exception.AuthenticationException;
import com.epam.gymcrm.exception.EntityNotFoundException;
import com.epam.gymcrm.exception.ValidationException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Operation failed: status=401, message={}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).body(errorResponse);
    }


    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException (ValidationException ex) {
        log.warn("Operation failed: status=400, message={}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(errorResponse);
    }


    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFoundException (EntityNotFoundException ex) {
        log.warn("Operation failed: status=404, message={}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).body(errorResponse);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException (IllegalStateException  ex) {
        log.warn("Operation failed: status=409, message={}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT.value()).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException (MethodArgumentNotValidException ex) {
        String message =
                ex.getBindingResult()
                        .getFieldErrors()
                        .get(0)
                        .getDefaultMessage();

        log.warn("Operation failed: status=400, message={}", message);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(errorResponse);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(
            Exception exception
    ) {
        log.warn(
                "Operation failed: status=400, message=Malformed request or invalid field format"
        );

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Malformed request or invalid field format"
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception exception
    ) {
        log.error(
                "Operation failed: status=500, message={}",
                exception.getMessage(),
                exception
        );

        ErrorResponse response = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Unexpected server error"
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

}

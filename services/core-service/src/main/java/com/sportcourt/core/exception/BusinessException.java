package com.sportcourt.core.exception;

import com.sportcourt.core.api.ApiErrorDetail;
import org.springframework.http.HttpStatus;

import java.util.List;

public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<ApiErrorDetail> details;

    public BusinessException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public BusinessException(HttpStatus status, String code, String message, List<ApiErrorDetail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<ApiErrorDetail> getDetails() {
        return details;
    }
}


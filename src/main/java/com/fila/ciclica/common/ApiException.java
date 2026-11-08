package com.fila.ciclica.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String detail;
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String code, String detail) {
        this(status, code, detail, null);
    }

    public ApiException(HttpStatus status, String code, String detail, Map<String, String> fields) {
        super(detail);
        this.status = status;
        this.code = code;
        this.detail = detail;
        this.fields = fields;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getDetail() {
        return detail;
    }

    public Map<String, String> getFields() {
        return fields;
    }
}

package com.nyzy.common;

/** 业务异常, 携带错误码 */
public class ApiException extends RuntimeException {
    private final int code;

    public ApiException(String message) {
        this(400, message);
    }

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

package com.alastbing.covid19.utils;

import java.io.Serializable;
import java.util.Objects;

/**
 * @ClassName: FeedResult
 * @Description: 平台统一返回结果
 * @Author: alas
 * @Date: 2020/2/16 9:33 下午
 * @Version: V1.0
 **/
public class FeedResult implements Serializable {
    private boolean status;
    private Integer errorCode;
    private String message;
    private Object result;

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FeedResult that = (FeedResult) o;
        return status == that.status &&
                Objects.equals(errorCode, that.errorCode) &&
                Objects.equals(message, that.message) &&
                Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, errorCode, message, result);
    }

    @Override
    public String toString() {
        return "FeedResult{" +
                "status=" + status +
                ", errorCode=" + errorCode +
                ", message='" + message + '\'' +
                ", result=" + result +
                '}';
    }
}

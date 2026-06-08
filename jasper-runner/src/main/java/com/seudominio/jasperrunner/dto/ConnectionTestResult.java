package com.seudominio.jasperrunner.dto;

public class ConnectionTestResult {

    private boolean success;
    private String message;

    public ConnectionTestResult() {}

    public ConnectionTestResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static ConnectionTestResult ok(String message) {
        return new ConnectionTestResult(true, message);
    }

    public static ConnectionTestResult fail(String message) {
        return new ConnectionTestResult(false, message);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

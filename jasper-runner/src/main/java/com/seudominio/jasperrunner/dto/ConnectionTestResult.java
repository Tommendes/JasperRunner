package com.seudominio.jasperrunner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionTestResult {

    private boolean success;
    private String message;

    public static ConnectionTestResult ok(String message) {
        return new ConnectionTestResult(true, message);
    }

    public static ConnectionTestResult fail(String message) {
        return new ConnectionTestResult(false, message);
    }
}

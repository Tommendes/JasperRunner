package com.seudominio.jasperrunner.util;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    private PasswordPolicy() {}

    public static boolean isValid(String password) {
        return password != null && password.length() >= MIN_LENGTH;
    }
}

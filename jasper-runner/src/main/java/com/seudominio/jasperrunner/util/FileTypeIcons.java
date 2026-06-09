package com.seudominio.jasperrunner.util;

import java.util.Locale;

public final class FileTypeIcons {

    private FileTypeIcons() {}

    /** Classes Bootstrap Icons para o tipo de arquivo (sem o prefixo {@code bi}). */
    public static String iconClasses(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "file-earmark text-muted";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jrxml")) return "file-earmark-bar-graph text-primary";
        if (lower.endsWith(".jasper")) return "file-earmark-code text-warning";
        if (lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".svg")) {
            return "file-earmark-image text-success";
        }
        if (lower.endsWith(".ttf")) return "fonts text-secondary";
        return "file-earmark text-muted";
    }
}

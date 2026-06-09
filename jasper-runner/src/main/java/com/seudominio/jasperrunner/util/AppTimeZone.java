package com.seudominio.jasperrunner.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Fuso da aplicação: Recife/PE (America/Recife, UTC−3). */
public final class AppTimeZone {

    public static final ZoneId DISPLAY_ZONE = ZoneId.of("America/Recife");
    private static final ZoneOffset STORE_OFFSET = ZoneOffset.UTC;
    private static final DateTimeFormatter DISPLAY_FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private AppTimeZone() {}

    /** Horário atual para persistência (UTC, sem informação de fuso no tipo). */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(STORE_OFFSET);
    }

    /** Converte timestamp absoluto do sistema de arquivos para horário de Recife. */
    public static LocalDateTime fromInstant(Instant instant) {
        return LocalDateTime.ofInstant(instant, DISPLAY_ZONE);
    }

    /** Exibe valores gravados no banco como UTC (created_at, updated_at). */
    public static String formatStoredUtc(LocalDateTime storedUtc) {
        if (storedUtc == null) return "";
        return storedUtc.atZone(STORE_OFFSET)
            .withZoneSameInstant(DISPLAY_ZONE)
            .format(DISPLAY_FMT);
    }

    /** Exibe LocalDateTime já em horário de Recife (ex.: lastModified de arquivo). */
    public static String formatLocal(LocalDateTime localRecife) {
        if (localRecife == null) return "";
        return localRecife.format(DISPLAY_FMT);
    }
}

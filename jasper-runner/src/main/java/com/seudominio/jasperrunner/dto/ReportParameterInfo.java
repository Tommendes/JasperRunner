package com.seudominio.jasperrunner.dto;

import lombok.Data;

/**
 * Metadados de um parâmetro JasperReports para renderização do formulário dinâmico.
 */
@Data
public class ReportParameterInfo {

    private String name;
    private String typeName;
    private String description;
    private String defaultValueAsString;

    /** Determina o tipo de input HTML adequado para o tipo Java do parâmetro. */
    public String getInputType() {
        if (typeName == null) return "text";
        return switch (typeName) {
            case "java.lang.Boolean" -> "checkbox";
            case "java.util.Date", "java.sql.Date" -> "date";
            case "java.sql.Timestamp" -> "datetime-local";
            case "java.lang.Integer", "java.lang.Long", "java.math.BigDecimal",
                 "java.lang.Double", "java.lang.Float", "java.lang.Short" -> "number";
            default -> "text";
        };
    }

    public boolean isBooleanType() {
        return "checkbox".equals(getInputType());
    }

    public boolean isNumberType() {
        return "number".equals(getInputType());
    }

    public boolean isDateType() {
        return "date".equals(getInputType()) || "datetime-local".equals(getInputType());
    }

    /** Retorna o nome simples do tipo (sem pacote). */
    public String getSimpleTypeName() {
        if (typeName == null) return "String";
        int lastDot = typeName.lastIndexOf('.');
        return lastDot >= 0 ? typeName.substring(lastDot + 1) : typeName;
    }
}

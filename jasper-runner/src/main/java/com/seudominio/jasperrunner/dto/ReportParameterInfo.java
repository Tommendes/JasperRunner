package com.seudominio.jasperrunner.dto;

/**
 * Metadados de um parâmetro JasperReports para renderização do formulário dinâmico.
 */
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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDefaultValueAsString() { return defaultValueAsString; }
    public void setDefaultValueAsString(String v) { this.defaultValueAsString = v; }
}

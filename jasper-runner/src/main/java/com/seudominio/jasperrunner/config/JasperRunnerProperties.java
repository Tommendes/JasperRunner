package com.seudominio.jasperrunner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jasperrunner")
public class JasperRunnerProperties {

    private String reportsRootPath = "./reports";
    private String adminUser = "admin";
    private String adminPassword = "changeme";
    private String adminName = "Administrator";
    private String adminEmail = "admin@localhost";
    private String baseUrl = "";
    private int passwordResetExpirationMinutes = 30;
    private String encryptionKey = "JasperRunner@2024$SecretKey!";

    public String getReportsRootPath() { return reportsRootPath; }
    public void setReportsRootPath(String reportsRootPath) { this.reportsRootPath = reportsRootPath; }

    public String getAdminUser() { return adminUser; }
    public void setAdminUser(String adminUser) { this.adminUser = adminUser; }

    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }

    public String getAdminName() { return adminName; }
    public void setAdminName(String adminName) { this.adminName = adminName; }

    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getPasswordResetExpirationMinutes() { return passwordResetExpirationMinutes; }
    public void setPasswordResetExpirationMinutes(int passwordResetExpirationMinutes) {
        this.passwordResetExpirationMinutes = passwordResetExpirationMinutes;
    }

    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }
}

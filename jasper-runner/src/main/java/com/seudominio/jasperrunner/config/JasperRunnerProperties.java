package com.seudominio.jasperrunner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jasperrunner")
public class JasperRunnerProperties {

    private String reportsRootPath = "./reports";
    private String adminUser = "admin";
    private String adminPassword = "changeme";
    private String encryptionKey = "JasperRunner@2024$SecretKey!";

    public String getReportsRootPath() { return reportsRootPath; }
    public void setReportsRootPath(String reportsRootPath) { this.reportsRootPath = reportsRootPath; }

    public String getAdminUser() { return adminUser; }
    public void setAdminUser(String adminUser) { this.adminUser = adminUser; }

    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }

    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }
}

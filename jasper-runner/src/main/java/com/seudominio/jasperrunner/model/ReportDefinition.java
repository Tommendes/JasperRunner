package com.seudominio.jasperrunner.model;

import jakarta.persistence.*;

import com.seudominio.jasperrunner.util.AppTimeZone;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "report_definitions")
public class ReportDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    /** Caminho relativo ao reports-root-path configurado em application.yml */
    @Column(name = "jrxml_path", nullable = false)
    private String jrxmlPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private ReportFolder folder;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = AppTimeZone.nowUtc();
        updatedAt = AppTimeZone.nowUtc();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = AppTimeZone.nowUtc();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getJrxmlPath() { return jrxmlPath; }
    public void setJrxmlPath(String jrxmlPath) { this.jrxmlPath = jrxmlPath; }

    /** Nome do arquivo no disco, com extensão (ex.: relatorio.jrxml). */
    public String getFileName() {
        if (jrxmlPath == null || jrxmlPath.isBlank()) return name;
        return Path.of(jrxmlPath).getFileName().toString();
    }

    public ReportFolder getFolder() { return folder; }
    public void setFolder(ReportFolder folder) { this.folder = folder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** Data exibida no grid: última alteração, ou criação se ainda não houve update. */
    public LocalDateTime getDisplayDate() {
        return updatedAt != null ? updatedAt : createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReportDefinition)) return false;
        ReportDefinition that = (ReportDefinition) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "ReportDefinition{id=" + id + ", name='" + name + "'}";
    }
}

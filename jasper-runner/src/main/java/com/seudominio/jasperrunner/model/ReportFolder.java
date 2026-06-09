package com.seudominio.jasperrunner.model;

import com.seudominio.jasperrunner.util.AppTimeZone;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "report_folders")
public class ReportFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ReportFolder parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("name ASC")
    private List<ReportFolder> children = new ArrayList<>();

    @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<ReportDefinition> reports = new ArrayList<>();

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

    public ReportFolder getParent() { return parent; }
    public void setParent(ReportFolder parent) { this.parent = parent; }

    public List<ReportFolder> getChildren() { return children; }
    public void setChildren(List<ReportFolder> children) { this.children = children; }

    public List<ReportDefinition> getReports() { return reports; }
    public void setReports(List<ReportDefinition> reports) { this.reports = reports; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReportFolder)) return false;
        ReportFolder that = (ReportFolder) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "ReportFolder{id=" + id + ", name='" + name + "'}";
    }
}

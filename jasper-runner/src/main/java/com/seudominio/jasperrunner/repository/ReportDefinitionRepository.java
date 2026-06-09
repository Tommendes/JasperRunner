package com.seudominio.jasperrunner.repository;

import com.seudominio.jasperrunner.model.ReportDefinition;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, Long> {
    List<ReportDefinition> findByFolderIsNull(Sort sort);
    List<ReportDefinition> findByFolderId(Long folderId, Sort sort);

    @Query("SELECT r.folder.id FROM ReportDefinition r WHERE r.id = :id")
    Optional<Long> findFolderIdByReportId(@Param("id") Long id);

    @Query("SELECT r FROM ReportDefinition r LEFT JOIN FETCH r.folder WHERE r.id = :id")
    Optional<ReportDefinition> findByIdWithFolder(@Param("id") Long id);

    Optional<ReportDefinition> findByJrxmlPath(String jrxmlPath);
}

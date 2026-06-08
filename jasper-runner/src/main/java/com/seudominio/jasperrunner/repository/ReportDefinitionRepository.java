package com.seudominio.jasperrunner.repository;

import com.seudominio.jasperrunner.model.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.List;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, Long> {
    List<ReportDefinition> findByFolderIsNull(Sort sort);
    List<ReportDefinition> findByFolderId(Long folderId, Sort sort);
}

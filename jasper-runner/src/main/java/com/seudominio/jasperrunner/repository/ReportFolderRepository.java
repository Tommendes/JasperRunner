package com.seudominio.jasperrunner.repository;

import com.seudominio.jasperrunner.model.ReportFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.List;

public interface ReportFolderRepository extends JpaRepository<ReportFolder, Long> {
    List<ReportFolder> findByParentIsNull(Sort sort);
    List<ReportFolder> findByParentId(Long parentId, Sort sort);
}

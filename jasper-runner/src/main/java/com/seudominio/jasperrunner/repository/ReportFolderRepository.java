package com.seudominio.jasperrunner.repository;

import com.seudominio.jasperrunner.model.ReportFolder;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportFolderRepository extends JpaRepository<ReportFolder, Long> {
    List<ReportFolder> findByParentIsNull(Sort sort);
    List<ReportFolder> findByParentId(Long parentId, Sort sort);

    @Query("SELECT f.parent.id FROM ReportFolder f WHERE f.id = :id")
    Optional<Long> findParentIdById(@Param("id") Long id);
}

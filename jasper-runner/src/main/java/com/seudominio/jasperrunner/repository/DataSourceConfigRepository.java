package com.seudominio.jasperrunner.repository;

import com.seudominio.jasperrunner.model.DataSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSourceConfigRepository extends JpaRepository<DataSourceConfig, Long> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
}

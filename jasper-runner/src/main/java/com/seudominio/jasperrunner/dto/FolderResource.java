package com.seudominio.jasperrunner.dto;

import java.time.LocalDateTime;

public record FolderResource(String fileName, String relativePath, LocalDateTime lastModified) {}

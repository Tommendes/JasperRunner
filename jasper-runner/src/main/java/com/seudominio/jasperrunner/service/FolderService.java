package com.seudominio.jasperrunner.service;

import com.seudominio.jasperrunner.model.ReportFolder;
import com.seudominio.jasperrunner.repository.ReportFolderRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class FolderService {

    private final ReportFolderRepository repository;

    public FolderService(ReportFolderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ReportFolder> getRootFolders() {
        return repository.findByParentIsNull(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public List<ReportFolder> findChildFolders(Long parentId) {
        return repository.findByParentId(parentId, Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public Optional<ReportFolder> findById(Long id) {
        return repository.findById(id);
    }

    public ReportFolder create(String name, String description, Long parentId) {
        ReportFolder folder = new ReportFolder();
        folder.setName(name);
        folder.setDescription(description);

        if (parentId != null) {
            repository.findById(parentId).ifPresent(folder::setParent);
        }

        return repository.save(folder);
    }

    public ReportFolder update(Long id, String name, String description) {
        ReportFolder folder = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Pasta não encontrada: " + id));
        folder.setName(name);
        folder.setDescription(description);
        return repository.save(folder);
    }

    public void move(Long folderId, Long newParentId) {
        ReportFolder folder = repository.findById(folderId)
            .orElseThrow(() -> new IllegalArgumentException("Pasta não encontrada: " + folderId));

        if (newParentId == null) {
            folder.setParent(null);
        } else {
            if (isAncestorOf(folderId, newParentId)) {
                throw new IllegalArgumentException("Não é possível mover uma pasta para dentro de si mesma ou de seus descendentes.");
            }
            ReportFolder newParent = repository.findById(newParentId)
                .orElseThrow(() -> new IllegalArgumentException("Pasta destino não encontrada: " + newParentId));
            folder.setParent(newParent);
        }

        repository.save(folder);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    /** Verifica se {@code ancestorId} é ancestral de {@code candidateId}. */
    private boolean isAncestorOf(Long ancestorId, Long candidateId) {
        if (ancestorId.equals(candidateId)) return true;
        return repository.findById(candidateId)
            .filter(f -> f.getParent() != null)
            .map(f -> isAncestorOf(ancestorId, f.getParent().getId()))
            .orElse(false);
    }
}

package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import com.digitalid.api.controller.models.DocumentStatus;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUser_IdOrderByUploadedAtDesc(Long userId);
    Optional<Document> findTopByUser_IdAndDocumentTypeOrderByUploadedAtDesc(Long userId, String documentType);
    Optional<Document> findByIdAndUser_Id(Long id, Long userId);
    int countByUser_IdAndDocumentType(Long userId, String documentType);
    List<Document> findAllByOrderByUploadedAtDesc();
    List<Document> findByStatusOrderByUploadedAtDesc(DocumentStatus status);
    List<Document> findByUser_IdInOrderByUploadedAtDesc(List<Long> userIds);
    long countByStatus(DocumentStatus status);
}

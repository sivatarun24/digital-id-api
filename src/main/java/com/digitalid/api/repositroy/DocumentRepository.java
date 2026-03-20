package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUser_IdOrderByUploadedAtDesc(Long userId);
    Optional<Document> findByIdAndUser_Id(Long id, Long userId);
}

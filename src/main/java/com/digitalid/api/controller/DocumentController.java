package com.digitalid.api.controller;

import com.digitalid.api.service.DocumentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/documents", produces = MediaType.APPLICATION_JSON_VALUE)
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getDocuments(Authentication auth) {
        return ResponseEntity.ok(documentService.getDocuments(auth.getName()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(
            Authentication auth,
            @RequestParam("documentType") String documentType,
            @RequestParam(value = "issuer", required = false) String issuer,
            @RequestParam(value = "expiresAt", required = false) String expiresAt,
            @RequestParam("file") MultipartFile file) throws IOException {

        Map<String, Object> doc = documentService.uploadDocument(
                auth.getName(), documentType, issuer, expiresAt, file);
        return ResponseEntity.ok(doc);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            Authentication auth,
            @PathVariable Long id) {
        documentService.deleteDocument(auth.getName(), id);
        return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
    }

    @GetMapping(value = "/{id}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> getDocumentFile(
            Authentication auth,
            @PathVariable Long id) {
        Resource resource = documentService.getDocumentFile(auth.getName(), id);
        String mimeType = documentService.getDocumentMimeType(auth.getName(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        mimeType != null ? mimeType : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }
}

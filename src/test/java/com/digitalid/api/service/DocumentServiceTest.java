package com.digitalid.api.service;

import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.Document;
import com.digitalid.api.controller.models.DocumentStatus;
import com.digitalid.api.controller.models.User;
import com.digitalid.api.repositroy.DocumentRepository;
import com.digitalid.api.repositroy.UserRepository;
import com.digitalid.api.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private StorageService storageService;

    @InjectMocks
    private DocumentService documentService;

    private User testUser;
    private Document testDocument;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .build();

        testDocument = Document.builder()
                .id(10L)
                .user(testUser)
                .documentType("drivers_license")
                .originalFileName("license.jpg")
                .filePath("/uploads/documents/1/1_drivers_license_1.jpg")
                .fileSize(1024L)
                .mimeType("image/jpeg")
                .status(DocumentStatus.PENDING)
                .build();
    }

    // ── getDocuments ──────────────────────────────────────────

    @Test
    void getDocuments_shouldReturnMappedList() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByUser_IdOrderByUploadedAtDesc(1L))
                .thenReturn(List.of(testDocument));

        List<Map<String, Object>> result = documentService.getDocuments("testuser");

        assertEquals(1, result.size());
        assertEquals("drivers_license", result.get(0).get("documentType"));
        assertEquals("pending", result.get(0).get("status"));
    }

    @Test
    void getDocuments_withEmptyList_shouldReturnEmpty() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByUser_IdOrderByUploadedAtDesc(1L)).thenReturn(List.of());

        List<Map<String, Object>> result = documentService.getDocuments("testuser");

        assertTrue(result.isEmpty());
    }

    @Test
    void getDocuments_withUnknownUser_shouldThrow404() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.getDocuments("nobody"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ── uploadDocument ────────────────────────────────────────

    @Test
    void uploadDocument_withValidJpeg_shouldStoreAndReturnMap() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "license.jpg", "image/jpeg", new byte[512]);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.countByUser_IdAndDocumentType(1L, "drivers_license"))
                .thenReturn(0);
        when(storageService.store(eq(1L), eq("drivers_license"), eq(1), eq("license.jpg"), any()))
                .thenReturn("/uploads/documents/1/1_drivers_license_1.jpg");
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);

        Map<String, Object> result = documentService.uploadDocument(
                "testuser", "drivers_license", "California", null, file);

        assertNotNull(result);
        assertEquals("drivers_license", result.get("documentType"));
        verify(storageService).store(eq(1L), eq("drivers_license"), eq(1), eq("license.jpg"), any());
        verify(documentRepository).save(any(Document.class));
        verify(notificationService).create(eq(1L), any(), any(), any());
        verify(auditLogService).log(eq("testuser"), any(), any());
    }

    @Test
    void uploadDocument_withValidPdf_shouldSucceed() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "passport.pdf", "application/pdf", new byte[512]);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.countByUser_IdAndDocumentType(1L, "passport")).thenReturn(0);
        when(storageService.store(any(), any(), anyInt(), any(), any()))
                .thenReturn("/uploads/documents/1/1_passport_1.pdf");
        when(documentRepository.save(any())).thenReturn(testDocument);

        assertDoesNotThrow(() -> documentService.uploadDocument(
                "testuser", "passport", null, null, file));
    }

    @Test
    void uploadDocument_sequenceNumberBasedOnExistingCount() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "license2.jpg", "image/jpeg", new byte[100]);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.countByUser_IdAndDocumentType(1L, "drivers_license"))
                .thenReturn(2); // already has 2 → next seq is 3
        when(storageService.store(eq(1L), eq("drivers_license"), eq(3), any(), any()))
                .thenReturn("/uploads/documents/1/1_drivers_license_3.jpg");
        when(documentRepository.save(any())).thenReturn(testDocument);

        documentService.uploadDocument("testuser", "drivers_license", null, null, file);

        verify(storageService).store(eq(1L), eq("drivers_license"), eq(3), any(), any());
    }

    @Test
    void uploadDocument_withEmptyFile_shouldThrow400() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.uploadDocument("testuser", "drivers_license", null, null, empty));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void uploadDocument_withNullFile_shouldThrow400() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.uploadDocument("testuser", "drivers_license", null, null, null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void uploadDocument_withBlankDocumentType_shouldThrow400() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "license.jpg", "image/jpeg", new byte[100]);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.uploadDocument("testuser", "  ", null, null, file));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void uploadDocument_withUnsupportedMimeType_shouldThrow400() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.exe", "application/octet-stream", new byte[100]);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.uploadDocument("testuser", "drivers_license", null, null, file));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void uploadDocument_exceeding10MB_shouldThrow400() {
        byte[] big = new byte[11 * 1024 * 1024]; // 11 MB
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", big);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.uploadDocument("testuser", "drivers_license", null, null, file));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void uploadDocument_exactlyAtSizeLimit_shouldSucceed() throws IOException {
        byte[] maxSize = new byte[10 * 1024 * 1024]; // exactly 10 MB
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", maxSize);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.countByUser_IdAndDocumentType(any(), any())).thenReturn(0);
        when(storageService.store(any(), any(), anyInt(), any(), any())).thenReturn("path");
        when(documentRepository.save(any())).thenReturn(testDocument);

        assertDoesNotThrow(() -> documentService.uploadDocument(
                "testuser", "drivers_license", null, null, file));
    }

    // ── deleteDocument ────────────────────────────────────────

    @Test
    void deleteDocument_shouldDeleteFromStorageAndRepo() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(10L, 1L))
                .thenReturn(Optional.of(testDocument));

        documentService.deleteDocument("testuser", 10L);

        verify(storageService).delete(testDocument.getFilePath());
        verify(documentRepository).delete(testDocument);
        verify(auditLogService).log(eq("testuser"), any(), any());
    }

    @Test
    void deleteDocument_withWrongOwner_shouldThrow404() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.deleteDocument("testuser", 10L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteDocument_withNonExistentId_shouldThrow404() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.deleteDocument("testuser", 99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(storageService);
    }

    // ── getDocumentFile ───────────────────────────────────────

    @Test
    void getDocumentFile_shouldReturnResourceFromStorage() throws IOException {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(10L, 1L))
                .thenReturn(Optional.of(testDocument));
        when(storageService.load(testDocument.getFilePath()))
                .thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));

        var resource = documentService.getDocumentFile("testuser", 10L);

        assertNotNull(resource);
        assertTrue(resource.isReadable());
        verify(storageService).load(testDocument.getFilePath());
    }

    @Test
    void getDocumentFile_withNotFound_shouldThrow404() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.getDocumentFile("testuser", 99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getDocumentFile_whenStorageThrows_shouldReturn500() throws IOException {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(10L, 1L))
                .thenReturn(Optional.of(testDocument));
        when(storageService.load(any())).thenThrow(new IOException("disk error"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.getDocumentFile("testuser", 10L));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
    }

    // ── replaceDocument ───────────────────────────────────────

    @Test
    void replaceDocument_withValidFile_shouldDeleteOldAndStoreNew() throws IOException {
        MockMultipartFile newFile = new MockMultipartFile(
                "file", "license_new.jpg", "image/jpeg", new byte[512]);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(10L, 1L))
                .thenReturn(Optional.of(testDocument));
        when(storageService.store(eq(1L), eq("drivers_license"), eq(10), eq("license_new.jpg"), any()))
                .thenReturn("/uploads/documents/1/1_drivers_license_10.jpg");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        String oldFilePath = testDocument.getFilePath();
        Map<String, Object> result = documentService.replaceDocument("testuser", 10L, newFile);

        assertNotNull(result);
        verify(storageService).delete(oldFilePath);
        verify(storageService).store(eq(1L), eq("drivers_license"), eq(10), eq("license_new.jpg"), any());
        verify(documentRepository).save(any(Document.class));
        verify(notificationService).create(eq(1L), any(), any(), any());
        verify(auditLogService).log(eq("testuser"), any(), contains("replaced"));
    }

    @Test
    void replaceDocument_setsStatusToVerified() throws IOException {
        testDocument.setStatus(DocumentStatus.VERIFIED);
        MockMultipartFile newFile = new MockMultipartFile(
                "file", "new.jpg", "image/jpeg", new byte[100]);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(10L, 1L))
                .thenReturn(Optional.of(testDocument));
        when(storageService.store(any(), any(), anyInt(), any(), any())).thenReturn("new/path.jpg");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        documentService.replaceDocument("testuser", 10L, newFile);

        assertEquals(DocumentStatus.VERIFIED, testDocument.getStatus());
    }

    @Test
    void replaceDocument_withNotFoundDocument_shouldThrow404() {
        MockMultipartFile newFile = new MockMultipartFile(
                "file", "new.jpg", "image/jpeg", new byte[100]);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.replaceDocument("testuser", 99L, newFile));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void replaceDocument_withEmptyFile_shouldThrow400() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(10L, 1L))
                .thenReturn(Optional.of(testDocument));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.replaceDocument("testuser", 10L, empty));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(storageService);
    }

    @Test
    void replaceDocument_withUnsupportedMimeType_shouldThrow400() {
        MockMultipartFile badFile = new MockMultipartFile(
                "file", "virus.exe", "application/octet-stream", new byte[100]);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(10L, 1L))
                .thenReturn(Optional.of(testDocument));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.replaceDocument("testuser", 10L, badFile));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(storageService);
    }

    @Test
    void replaceDocument_exceedingSizeLimit_shouldThrow400() {
        byte[] big = new byte[11 * 1024 * 1024];
        MockMultipartFile bigFile = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", big);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(10L, 1L))
                .thenReturn(Optional.of(testDocument));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.replaceDocument("testuser", 10L, bigFile));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void replaceDocument_updatesFilenameAndMimeType() throws IOException {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "updated.pdf", "application/pdf", new byte[200]);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(10L, 1L))
                .thenReturn(Optional.of(testDocument));
        when(storageService.store(any(), any(), anyInt(), any(), any())).thenReturn("new/path.pdf");
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        documentService.replaceDocument("testuser", 10L, pdfFile);

        assertEquals("updated.pdf", testDocument.getOriginalFileName());
        assertEquals("application/pdf", testDocument.getMimeType());
    }

    // ── getDocumentMimeType ───────────────────────────────────

    @Test
    void getDocumentMimeType_shouldReturnMimeType() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(10L, 1L))
                .thenReturn(Optional.of(testDocument));

        String mimeType = documentService.getDocumentMimeType("testuser", 10L);

        assertEquals("image/jpeg", mimeType);
    }

    @Test
    void getDocumentMimeType_withNotFound_shouldThrow404() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(documentRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> documentService.getDocumentMimeType("testuser", 99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}

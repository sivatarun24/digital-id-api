package com.digitalid.api.service;

import com.digitalid.api.audit.AuditAction;
import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.Document;
import com.digitalid.api.controller.models.DocumentStatus;
import com.digitalid.api.controller.models.IdentityVerification;
import com.digitalid.api.controller.models.User;
import com.digitalid.api.controller.models.VerificationStatus;
import com.digitalid.api.repositroy.DocumentRepository;
import com.digitalid.api.repositroy.IdentityVerificationRepository;
import com.digitalid.api.repositroy.UserRepository;
<<<<<<< Updated upstream
import com.digitalid.api.service.ocr.CredentialAnalyzer;
import com.digitalid.api.service.ocr.FaceMatchingService;
import com.digitalid.api.service.ocr.OcrResult;
import com.digitalid.api.service.ocr.OcrService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
=======
import com.digitalid.api.service.storage.StorageService;
>>>>>>> Stashed changes
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
public class IdentityVerificationService {

    private final IdentityVerificationRepository repo;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
<<<<<<< Updated upstream
    private final OcrService ocrService;
    private final CredentialAnalyzer credentialAnalyzer;
    private final FaceMatchingService faceMatchingService;
=======
    private final StorageService storageService;
>>>>>>> Stashed changes

    /** Maps the wizard's idType to the canonical document type stored in the documents table. */
    private static final Map<String, String> ID_TYPE_TO_DOC_TYPE = Map.of(
            "drivers_license", "drivers_license",
            "passport",        "passport",
            "state_id",        "state_id",
            "military_id",     "military_id",
            "passport_card",   "passport"
    );

    public IdentityVerificationService(IdentityVerificationRepository repo,
                                        UserRepository userRepository,
                                        DocumentRepository documentRepository,
                                        NotificationService notificationService,
                                        AuditLogService auditLogService,
<<<<<<< Updated upstream
                                        OcrService ocrService,
                                        CredentialAnalyzer credentialAnalyzer,
                                        FaceMatchingService faceMatchingService) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.ocrService = ocrService;
        this.credentialAnalyzer = credentialAnalyzer;
        this.faceMatchingService = faceMatchingService;
=======
                                        StorageService storageService) {
        this.repo               = repo;
        this.userRepository     = userRepository;
        this.documentRepository = documentRepository;
        this.notificationService = notificationService;
        this.auditLogService    = auditLogService;
        this.storageService     = storageService;
>>>>>>> Stashed changes
    }

    public Map<String, Object> getStatus(String username) {
        User user = getUser(username);
        Optional<IdentityVerification> latest = repo.findTopByUserIdOrderBySubmittedAtDesc(user.getId());
        if (latest.isEmpty()) {
            return Map.of("status", "none");
        }
        IdentityVerification iv = latest.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status",        iv.getStatus().name().toLowerCase());
        result.put("id",            iv.getId());
        result.put("idType",        iv.getIdType());
        result.put("submittedAt",   iv.getSubmittedAt().toLocalDate().toString());
        result.put("reviewedAt",    iv.getReviewedAt() != null
                ? iv.getReviewedAt().toLocalDate().toString() : null);
<<<<<<< Updated upstream
        String notes = iv.getReviewerNotes();
        if (notes != null) {
            // Remove legacy or AI generated prefixes (AI, Automated system, Check Failed, etc.)
            // Using a more aggressive regex to catch any variation
            notes = notes.replaceAll("(?i)^(AI|Automated system|Check Failed|Verification Failed):\\s*", "");
        }
        result.put("reviewerNotes", notes);
        result.put("hasFrontFile", iv.getFrontFilePath() != null);
        result.put("hasBackFile", iv.getBackFilePath() != null);
=======
        result.put("reviewerNotes", iv.getReviewerNotes());
        result.put("hasFrontFile",  iv.getFrontFilePath() != null);
        result.put("hasBackFile",   iv.getBackFilePath() != null);
>>>>>>> Stashed changes
        result.put("hasSelfieFile", iv.getSelfieFilePath() != null);
        return result;
    }

    public Map<String, Object> submit(String username, String idType,
                                       MultipartFile frontFile,
                                       MultipartFile backFile,
                                       MultipartFile selfieFile) throws IOException {
        User user = getUser(username);

        if (idType == null || idType.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID type is required");
        if (frontFile == null || frontFile.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Front image is required");
        if (selfieFile == null || selfieFile.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selfie is required");

        String docType = ID_TYPE_TO_DOC_TYPE.getOrDefault(idType, idType);

        // ── Store front file + create Document record ──────────────────────────
        int frontSeq = documentRepository.countByUser_IdAndDocumentType(user.getId(), docType) + 1;
        String frontPath = storageService.store(
                user.getId(), docType, frontSeq, frontFile.getOriginalFilename(), frontFile);
        Document frontDoc = documentRepository.save(Document.builder()
                .user(user).documentType(docType).status(DocumentStatus.PENDING)
                .originalFileName(frontFile.getOriginalFilename())
                .filePath(frontPath).fileSize(frontFile.getSize()).mimeType(frontFile.getContentType())
                .build());

        // ── Store back file + create Document record (optional) ────────────────
        String backPath = null;
        Document backDoc = null;
        if (backFile != null && !backFile.isEmpty()) {
            int backSeq = documentRepository.countByUser_IdAndDocumentType(user.getId(), docType) + 1;
            backPath = storageService.store(
                    user.getId(), docType, backSeq, backFile.getOriginalFilename(), backFile);
            backDoc = documentRepository.save(Document.builder()
                    .user(user).documentType(docType).status(DocumentStatus.PENDING)
                    .originalFileName(backFile.getOriginalFilename())
                    .filePath(backPath).fileSize(backFile.getSize()).mimeType(backFile.getContentType())
                    .build());
        }

        // ── Store selfie — no Document record for selfies ──────────────────────
        int selfieSeq = documentRepository.countByUser_IdAndDocumentType(user.getId(), "selfie") + 1;
        String selfiePath = storageService.store(
                user.getId(), "selfie", selfieSeq, selfieFile.getOriginalFilename(), selfieFile);

        // ── Save verification record ───────────────────────────────────────────
        IdentityVerification iv = IdentityVerification.builder()
                .userId(user.getId())
                .idType(idType)
                .frontFilePath(frontPath)
                .backFilePath(backPath)
                .selfieFilePath(selfiePath)
                .status(VerificationStatus.PENDING)
                .build();
        iv = repo.save(iv);

<<<<<<< Updated upstream
        // --- Automated System Verification Analysis ---
        // To match the user's requirement for a real-time "pending" status,
        // we simulate a processing delay for the AI models.
        try {
            Thread.sleep(2000); // 2-second simulation
        } catch (InterruptedException ignored) {}

        OcrResult ocrResult = ocrService.extractText(new File(frontPath));
        FaceMatchingService.MatchResult faceResult = faceMatchingService.matchFaces(new File(frontPath), new File(selfiePath));
        
        System.out.println("Identity Verification Analysis - User: " + username);
        System.out.println("Expected Name: " + user.getName());
        System.out.println("OCR Success: " + ocrResult.isSuccess());
        if (ocrResult.isSuccess()) {
            System.out.println("Raw OCR Text: " + ocrResult.getRawText().replace("\n", " | "));
        }
        System.out.println("Face Match: " + faceResult.isMatch() + " (Confidence: " + faceResult.confidence() + ")");

        boolean autoVerified = false;
        String failureReason = null;

        if (ocrResult.isSuccess()) {
            CredentialAnalyzer.AnalyzeResult analyzeResult = 
                    credentialAnalyzer.analyze(ocrResult.getRawText(), user.getName(), idType);
            
            System.out.println("OCR Analyze Match: " + analyzeResult.isMatch() + " (Confidence: " + analyzeResult.confidence() + ")");

            if (analyzeResult.isMatch()) {
                if (faceResult.isMatch()) {
                    iv.setStatus(VerificationStatus.VERIFIED);
                    iv.setReviewedAt(LocalDateTime.now());
                    iv.setReviewerNotes("Identity confirmed by automated OCR and biometric matching.");
                    iv = repo.save(iv);
                    autoVerified = true;
                    
                    notificationService.create(user.getId(), "verification",
                            "Identity Verified!",
                            "Our automated system has confirmed your identity from your " + idType.replace("_", " ") + " and biometric check.");
                    
                    auditLogService.log(username, AuditAction.IDENTITY_VERIFY_APPROVED,
                            idType + " verified (OCR confidence: " + String.format("%.2f", analyzeResult.confidence()) + 
                            ", Biometric confidence: " + String.format("%.2f", faceResult.confidence()) + ")");
                } else {
                    failureReason = faceResult.message();
                }
            } else {
                failureReason = analyzeResult.message();
            }
        } else {
            failureReason = ocrResult.getErrorMessage() != null ? ocrResult.getErrorMessage() : "The uploaded document could not be analyzed. Please ensure it is clear and well-lit.";
        }

        if (!autoVerified) {
            System.out.println("Verification failed. Reason: " + failureReason);
            if (failureReason != null) {
                // Clean AI/System prefixes - refined regex to only match at the very beginning
                failureReason = failureReason.replaceAll("(?i)^(AI|Automated system|Check Failed|Verification Failed):\\s*", "");
                
                iv.setStatus(VerificationStatus.REJECTED);
                iv.setReviewedAt(LocalDateTime.now());
                iv.setReviewerNotes(failureReason);
                iv = repo.save(iv);
                
                String notificationMsg = "Face Match Failed: " + failureReason;
                notificationService.create(user.getId(), "verification", "Verification Rejected", notificationMsg);
                auditLogService.log(username, AuditAction.IDENTITY_VERIFY_REJECTED, idType + ": " + failureReason);
            } else {
                notificationService.create(user.getId(), "verification", "Identity verification submitted", "Your identity documents have been received and are under review.");
                auditLogService.log(username, AuditAction.IDENTITY_VERIFY_SUBMITTED, idType);
            }
        }
=======
        notificationService.create(user.getId(), "verification",
                "Identity verification submitted",
                "Your identity documents have been received and are under review. " +
                "You'll be notified once the review is complete.");
        auditLogService.log(username, AuditAction.IDENTITY_VERIFY_SUBMITTED, idType);
>>>>>>> Stashed changes

        // ── Build response ─────────────────────────────────────────────────────
        Map<String, Object> result = new LinkedHashMap<>();
<<<<<<< Updated upstream
        result.put("status", iv.getStatus().name().toLowerCase());
        result.put("id", iv.getId());
        result.put("idType", iv.getIdType());
        result.put("submittedAt", iv.getSubmittedAt().toLocalDate().toString());
        result.put("autoVerified", autoVerified);
        String notes = iv.getReviewerNotes();
        if (notes != null) {
            notes = notes.replaceAll("(?i)^(AI|Automated system|Check Failed|Verification Failed):\\s*", "");
        }
        result.put("reviewerNotes", notes);
        result.put("hasFrontFile", iv.getFrontFilePath() != null);
        result.put("hasBackFile", iv.getBackFilePath() != null);
=======
        result.put("status",        iv.getStatus().name().toLowerCase());
        result.put("id",            iv.getId());
        result.put("idType",        iv.getIdType());
        result.put("submittedAt",   iv.getSubmittedAt().toLocalDate().toString());
        result.put("reviewedAt",    null);
        result.put("reviewerNotes", null);
        result.put("hasFrontFile",  iv.getFrontFilePath() != null);
        result.put("hasBackFile",   iv.getBackFilePath() != null);
>>>>>>> Stashed changes
        result.put("hasSelfieFile", iv.getSelfieFilePath() != null);

        // Include created Document records so the frontend can update its list
        List<Map<String, Object>> docs = new ArrayList<>();
        docs.add(toDocMap(frontDoc));
        if (backDoc != null) docs.add(toDocMap(backDoc));
        result.put("documents", docs);

        return result;
    }

    public boolean isVerified(Long userId) {
        return repo.existsByUserIdAndStatus(userId, VerificationStatus.VERIFIED);
    }

    public Resource getVerificationFile(String username, String side) {
        IdentityVerification iv = getLatestVerification(username);
        String path = switch (side.toLowerCase()) {
            case "front"  -> iv.getFrontFilePath();
            case "back"   -> iv.getBackFilePath();
            case "selfie" -> iv.getSelfieFilePath();
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid side. Use front, back, or selfie");
        };
        if (path == null || path.isBlank())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not provided");
        try {
            return storageService.load(path);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
    }

    public String getVerificationFileMimeType(String username, String side) {
        IdentityVerification iv = getLatestVerification(username);
        String path = switch (side.toLowerCase()) {
            case "front"  -> iv.getFrontFilePath();
            case "back"   -> iv.getBackFilePath();
            case "selfie" -> iv.getSelfieFilePath();
            default -> null;
        };
        if (path == null) return "application/octet-stream";
        String lower = path.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> toDocMap(Document doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",               doc.getId());
        map.put("documentType",     doc.getDocumentType());
        map.put("issuer",           doc.getIssuer());
        map.put("status",           doc.getStatus().name().toLowerCase());
        map.put("originalFileName", doc.getOriginalFileName());
        map.put("fileSize",         doc.getFileSize());
        map.put("mimeType",         doc.getMimeType());
        map.put("expiresAt",        null);
        map.put("uploadedAt",       doc.getUploadedAt() != null
                ? doc.getUploadedAt().toLocalDate().toString() : null);
        return map;
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private IdentityVerification getLatestVerification(String username) {
        User user = getUser(username);
        return repo.findTopByUserIdOrderBySubmittedAtDesc(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification not found"));
    }
}

package com.digitalid.api.service;

import com.digitalid.api.audit.AuditAction;
import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.*;
import com.digitalid.api.repositroy.*;
import com.digitalid.api.service.ocr.CredentialAnalyzer;
import com.digitalid.api.service.ocr.OcrResult;
import com.digitalid.api.service.ocr.OcrService;
import com.digitalid.api.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class CredentialService {

    private static final Logger log = LoggerFactory.getLogger(CredentialService.class);

    private static final Set<String> AVAILABLE_TYPES = Set.of(
            "military", "student", "first_responder", "teacher",
            "healthcare", "government", "senior", "nonprofit"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf"
    );

    private final UserCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final DocumentRepository documentRepository;
    private final MilitaryCredentialDetailsRepository militaryDetailsRepository;
    private final StudentCredentialDetailsRepository studentDetailsRepository;
    private final FirstResponderCredentialDetailsRepository firstResponderDetailsRepository;
    private final TeacherCredentialDetailsRepository teacherDetailsRepository;
    private final HealthcareCredentialDetailsRepository healthcareDetailsRepository;
    private final GovernmentCredentialDetailsRepository governmentDetailsRepository;
    private final SeniorCredentialDetailsRepository seniorDetailsRepository;
    private final NonprofitCredentialDetailsRepository nonprofitDetailsRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final VerificationAutomationService automationService;
    private final OcrService ocrService;
    private final CredentialAnalyzer credentialAnalyzer;

    public CredentialService(UserCredentialRepository credentialRepository,
                              UserRepository userRepository,
                              IdentityVerificationRepository identityVerificationRepository,
                              DocumentRepository documentRepository,
                              MilitaryCredentialDetailsRepository militaryDetailsRepository,
                              StudentCredentialDetailsRepository studentDetailsRepository,
                             FirstResponderCredentialDetailsRepository firstResponderDetailsRepository,
                             TeacherCredentialDetailsRepository teacherDetailsRepository,
                             HealthcareCredentialDetailsRepository healthcareDetailsRepository,
                             GovernmentCredentialDetailsRepository governmentDetailsRepository,
                             SeniorCredentialDetailsRepository seniorDetailsRepository,
                             NonprofitCredentialDetailsRepository nonprofitDetailsRepository,
                             StorageService storageService,
                             NotificationService notificationService,
                             AuditLogService auditLogService,
                             VerificationAutomationService automationService,
                             OcrService ocrService,
                             CredentialAnalyzer credentialAnalyzer) {
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
        this.identityVerificationRepository = identityVerificationRepository;
        this.documentRepository = documentRepository;
        this.militaryDetailsRepository = militaryDetailsRepository;
        this.studentDetailsRepository = studentDetailsRepository;
        this.firstResponderDetailsRepository = firstResponderDetailsRepository;
        this.teacherDetailsRepository = teacherDetailsRepository;
        this.healthcareDetailsRepository = healthcareDetailsRepository;
        this.governmentDetailsRepository = governmentDetailsRepository;
        this.seniorDetailsRepository = seniorDetailsRepository;
        this.nonprofitDetailsRepository = nonprofitDetailsRepository;
        this.storageService = storageService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.automationService = automationService;
        this.ocrService = ocrService;
        this.credentialAnalyzer = credentialAnalyzer;
    }

    public List<Map<String, Object>> getCredentials(String username) {
        User user = getUser(username);
        return credentialRepository.findByUserId(user.getId())
                .stream().map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> startVerification(String username, String credentialType, Map<String, String> fields, MultipartFile file) {
        User user = getUser(username);

        if (!AVAILABLE_TYPES.contains(credentialType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid credential type");
        }

        // Must have identity verification
        boolean identityVerified = identityVerificationRepository
                .existsByUserIdAndStatus(user.getId(), VerificationStatus.VERIFIED);
        if (!identityVerified) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Identity verification required before adding credentials");
        }

        UserCredential cred = credentialRepository.findByUserIdAndCredentialType(user.getId(), credentialType)
                .orElse(null);

        if (cred != null && cred.getStatus() == VerificationStatus.VERIFIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Credential already verified");
        }

        if (cred == null) {
            cred = UserCredential.builder()
                    .userId(user.getId())
                    .credentialType(credentialType)
                    .status(VerificationStatus.PENDING)
                    .build();
        } else {
            cred.setStatus(VerificationStatus.PENDING);
            cred.setStartedAt(LocalDateTime.now());
            cred.setReviewedAt(null);
            cred.setVerifiedAt(null);
            cred.setReviewerNotes(null);
        }
        cred = credentialRepository.save(cred);
        saveCredentialDetails(cred, fields != null ? fields : Map.of());

        if (file != null && !file.isEmpty()) {
            upsertSupportingDocument(user, credentialType, file);
        }

        notificationService.create(user.getId(), "verification",
                "Credential verification started",
                capitalize(credentialType.replace("_", " ")) + " affiliation verification is in progress.");
        auditLogService.log(username, AuditAction.CREDENTIAL_VERIFY_STARTED, credentialType);

        return toMap(cred);
    }

    @jakarta.transaction.Transactional
    public void deleteCredential(Long id) {
        UserCredential cred = credentialRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found"));

        // 1. Delete category-specific details
        switch (cred.getCredentialType()) {
            case "military" -> militaryDetailsRepository.deleteByUserCredentialId(cred.getId());
            case "student" -> studentDetailsRepository.deleteByUserCredentialId(cred.getId());
            case "first_responder" -> firstResponderDetailsRepository.deleteByUserCredentialId(cred.getId());
            case "teacher" -> teacherDetailsRepository.deleteByUserCredentialId(cred.getId());
            case "healthcare" -> healthcareDetailsRepository.deleteByUserCredentialId(cred.getId());
            case "government" -> governmentDetailsRepository.deleteByUserCredentialId(cred.getId());
            case "senior" -> seniorDetailsRepository.deleteByUserCredentialId(cred.getId());
            case "nonprofit" -> nonprofitDetailsRepository.deleteByUserCredentialId(cred.getId());
        }

        // 2. Delete associated documents and files
        documentRepository.findTopByUser_IdAndDocumentTypeOrderByUploadedAtDesc(cred.getUserId(), cred.getCredentialType())
                .ifPresent(doc -> {
                    storageService.delete(doc.getFilePath());
                    documentRepository.delete(doc);
                });

        // 3. Delete the credential itself
        credentialRepository.delete(cred);

        // 4. Audit
        userRepository.findById(cred.getUserId()).ifPresent(u -> {
            auditLogService.log(u.getUsername(), AuditAction.DOCUMENT_DELETE,
                    "Deleted credential " + cred.getCredentialType() + " (ID: " + id + ")");
        });
    }

    public Map<String, Object> submitDocument(String username, String credentialType, MultipartFile file, String verificationEmail) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supporting document is required");
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME_TYPES.contains(mime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG, WebP, and PDF files are accepted");
        }

        User user = getUser(username);

        UserCredential cred = credentialRepository.findByUserIdAndCredentialType(user.getId(), credentialType)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Credential not started. Call /start first."));

        if (cred.getStatus() != VerificationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Document can only be submitted for credentials in PENDING status");
        }

        upsertSupportingDocument(user, credentialType, file);

        // --- NEW: Sequential Verification Flow ---
        if (verificationEmail != null && !verificationEmail.isBlank()) {
            // Professional Account: Phase 1 is Email Verification
            String token = automationService.generateToken();
            cred.setVerificationEmail(verificationEmail);
            cred.setVerificationToken(token);
            cred.setStatus(VerificationStatus.PENDING);
            credentialRepository.save(cred);
            
            automationService.sendVerificationEmail(verificationEmail, user.getUsername(), credentialType, token);
            auditLogService.log(username, AuditAction.CREDENTIAL_VERIFY_STARTED,
                    credentialType + " sequential-verify started: email sent to " + verificationEmail);
            
            return Map.of(
                "message", "Document received! Please check your official email (" + verificationEmail + ") to verify your affiliation. Document analysis will begin after email confirmation.",
                "credential", toMap(cred),
                "autoVerified", false
            );
        }

        // --- No Email Provided (e.g. Senior): Immediate AI OCR ---
        credentialRepository.save(cred);
        log.info("[Credential] Running OCR on submitted document — user={}, type={}, file={}, size={} bytes",
                username, credentialType, file.getOriginalFilename(), file.getSize());
        OcrResult ocrResult = ocrService.extractText(file);
        log.info("[Credential] OCR result — success={}", ocrResult.isSuccess());
        if (ocrResult.isSuccess()) {
            log.debug("[Credential] OCR raw text: [{}]",
                    ocrResult.getRawText().replace("\n", " | "));
        } else {
            log.warn("[Credential] OCR failed: {}", ocrResult.getErrorMessage());
        }

        if (ocrResult.isSuccess()) {
            CredentialAnalyzer.AnalyzeResult analyzeResult =
                    credentialAnalyzer.analyze(ocrResult.getRawText(), user.getName(), credentialType);
            log.info("[Credential] Analyze result — match={} (conf={:.4f}): {}",
                    analyzeResult.isMatch(), analyzeResult.confidence(), analyzeResult.message());

            if (analyzeResult.isMatch()) {
                cred.setStatus(VerificationStatus.VERIFIED);
                cred.setVerifiedAt(LocalDateTime.now());
                credentialRepository.save(cred);

                // Sync Document Status
                documentRepository.findTopByUser_IdAndDocumentTypeOrderByUploadedAtDesc(user.getId(), credentialType)
                        .ifPresent(doc -> {
                            doc.setStatus(DocumentStatus.VERIFIED);
                            documentRepository.save(doc);
                        });
                
                notificationService.create(user.getId(), "verification",
                        "Credential Auto-Verified!",
                        "System analysis confirmed your " + capitalize(credentialType.replace("_", " ")) + " status.");
                
                auditLogService.log(username, AuditAction.CREDENTIAL_VERIFY_SUCCESS,
                        credentialType + " auto-verified via document analysis (No email path)");
                
                return Map.of(
                    "message", "Document analysis complete. Your credential has been automatically verified!",
                    "credential", toMap(cred),
                    "autoVerified", true
                );
            } else {
                cred.setStatus(VerificationStatus.REJECTED);
                cred.setReviewerNotes("Verification Failed: " + analyzeResult.message());
                credentialRepository.save(cred);

                // Sync Document Status
                documentRepository.findTopByUser_IdAndDocumentTypeOrderByUploadedAtDesc(user.getId(), credentialType)
                        .ifPresent(doc -> {
                            doc.setStatus(DocumentStatus.REJECTED);
                            documentRepository.save(doc);
                        });

                auditLogService.log(username, AuditAction.CREDENTIAL_VERIFY_FAILED,
                        credentialType + " document mismatch: " + analyzeResult.message());

                return Map.of(
                    "message", "Document analysis complete. Verification failed: " + analyzeResult.message(),
                    "credential", toMap(cred),
                    "autoVerified", true
                );
            }
        }

        notificationService.create(user.getId(), "verification",
                "Supporting document received",
                capitalize(credentialType.replace("_", " ")) + " document submitted and under review.");
        
        auditLogService.log(username, AuditAction.CREDENTIAL_VERIFY_STARTED,
                credentialType + " document submitted");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Document submitted successfully. " + 
                (verificationEmail != null ? "Please check your email to complete verification." : "Our team will review it shortly."));
        result.put("credential", toMap(cred));
        result.put("autoVerified", false);
        return result;
    }

    public Map<String, Object> requestEmailVerification(String username, String credentialType, String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }

        if (!automationService.isEligibleForInstantVerify(email, credentialType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "This email domain is not eligible for instant verification for " + credentialType);
        }

        User user = getUser(username);
        UserCredential cred = credentialRepository.findByUserIdAndCredentialType(user.getId(), credentialType)
                .orElse(UserCredential.builder()
                        .userId(user.getId())
                        .credentialType(credentialType)
                        .status(VerificationStatus.PENDING)
                        .build());

        if (cred.getStatus() == VerificationStatus.VERIFIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Credential already verified");
        }

        String token = automationService.generateToken();
        cred.setVerificationEmail(email);
        cred.setVerificationToken(token);
        cred.setStatus(VerificationStatus.PENDING);
        credentialRepository.save(cred);

        automationService.sendVerificationEmail(email, user.getUsername(), credentialType, token);
        auditLogService.log(username, AuditAction.CREDENTIAL_VERIFY_STARTED, 
                credentialType + " email verification requested: " + email);

        return Map.of("message", "Verification email sent to " + email);
    }

    public Map<String, Object> verifyEmailToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token is required");
        }

        UserCredential cred = credentialRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid or expired token"));

        User user = userRepository.findById(cred.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        cred.setVerifiedAt(LocalDateTime.now());
        cred.setVerificationToken(null); // Clear token
        // Keep status as PENDING - it will transition to VERIFIED after document analysis
        credentialRepository.save(cred);

        // --- NEW: Trigger AI OCR Analysis AFTER Email Verification ---
        documentRepository.findTopByUser_IdAndDocumentTypeOrderByUploadedAtDesc(user.getId(), cred.getCredentialType())
                .ifPresent(doc -> {
                    // We have a document, let's run AI analysis
                    try {
                        Resource resource = storageService.load(doc.getFilePath());
                        File tempFile = Files.createTempFile("ocr_deferred_", "_" + doc.getOriginalFileName()).toFile();
                        
                        try (InputStream is = resource.getInputStream()) {
                            Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                        
                        // Use the File-based OCR method
                        OcrResult ocrResult = ocrService.extractText(tempFile);
                        Files.deleteIfExists(tempFile.toPath());
                        
                        if (ocrResult.isSuccess()) {
                            CredentialAnalyzer.AnalyzeResult analyzeResult = 
                                    credentialAnalyzer.analyze(ocrResult.getRawText(), user.getName(), cred.getCredentialType());
                            
                            if (analyzeResult.isMatch()) {
                                cred.setStatus(VerificationStatus.VERIFIED);
                                cred.setReviewedAt(LocalDateTime.now());
                                credentialRepository.save(cred);

                                // Sync Document Status
                                doc.setStatus(DocumentStatus.VERIFIED);
                                documentRepository.save(doc);
                                
                                auditLogService.log(user.getUsername(), AuditAction.CREDENTIAL_VERIFY_SUCCESS, 
                                        cred.getCredentialType() + " confirmed via document analysis after email verification.");
                            } else {
                                // Email verified but validation FAILS!
                                cred.setStatus(VerificationStatus.REJECTED);
                                cred.setReviewerNotes("Email verified, but document validation failed: " + analyzeResult.message());
                                cred.setReviewedAt(LocalDateTime.now());
                                credentialRepository.save(cred);

                                // Sync Document Status
                                doc.setStatus(DocumentStatus.REJECTED);
                                documentRepository.save(doc);
                                
                                auditLogService.log(user.getUsername(), AuditAction.CREDENTIAL_VERIFY_FAILED, 
                                        cred.getCredentialType() + " email verified but validation failed: " + analyzeResult.message());
                            }
                        }
                    } catch (Exception e) {
                        // Fallback to manual review if validation fails or error occurs
                        auditLogService.log(user.getUsername(), AuditAction.CREDENTIAL_VERIFY_STARTED, 
                                "System Analysis skipped or failed after email verification for " + cred.getCredentialType());
                    }
                });

        notificationService.create(user.getId(), "verification",
                "Credential Email Verified!",
                capitalize(cred.getCredentialType().replace("_", " ")) + " email confirmed. Finalizing status...");
        
        return Map.of("message", "Email verified successfully. Status updated based on dual-check.", "credentialType", cred.getCredentialType());
    }

    public long countVerified(Long userId) {
        return credentialRepository.countByUserIdAndStatus(userId, VerificationStatus.VERIFIED);
    }

    public List<Map<String, Object>> getForWallet(Long userId) {
        return credentialRepository.findByUserId(userId)
                .stream().map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> getCredentialFields(UserCredential credential) {
        return switch (credential.getCredentialType()) {
            case "military" -> militaryDetailsRepository.findByUserCredentialId(credential.getId())
                    .map(d -> mapOf(
                            "branch", d.getBranch(),
                            "rank", d.getRank(),
                            "serviceStartDate", formatDate(d.getServiceStartDate()),
                            "currentlyServing", d.getCurrentlyServing(),
                            "serviceEndDate", formatDate(d.getServiceEndDate()),
                            "dischargeType", d.getDischargeType()
                    )).orElse(Map.of());
            case "student" -> studentDetailsRepository.findByUserCredentialId(credential.getId())
                    .map(d -> mapOf(
                            "schoolName", d.getSchoolName(),
                            "enrollmentStatus", d.getEnrollmentStatus(),
                            "major", d.getMajor(),
                            "studentId", d.getStudentId(),
                            "graduationDate", d.getGraduationDate()
                    )).orElse(Map.of());
            case "first_responder" -> firstResponderDetailsRepository.findByUserCredentialId(credential.getId())
                    .map(d -> mapOf(
                            "agencyName", d.getAgencyName(),
                            "role", d.getRole(),
                            "badgeNumber", d.getBadgeNumber(),
                            "employmentStartDate", formatDate(d.getEmploymentStartDate())
                    )).orElse(Map.of());
            case "teacher" -> teacherDetailsRepository.findByUserCredentialId(credential.getId())
                    .map(d -> mapOf(
                            "schoolName", d.getSchoolName(),
                            "teachingLevel", d.getTeachingLevel(),
                            "subject", d.getSubject(),
                            "employeeId", d.getEmployeeId(),
                            "employmentStartDate", formatDate(d.getEmploymentStartDate())
                    )).orElse(Map.of());
            case "healthcare" -> healthcareDetailsRepository.findByUserCredentialId(credential.getId())
                    .map(d -> mapOf(
                            "licenseType", d.getLicenseType(),
                            "licenseNumber", d.getLicenseNumber(),
                            "issuingState", d.getIssuingState(),
                            "employer", d.getEmployer()
                    )).orElse(Map.of());
            case "government" -> governmentDetailsRepository.findByUserCredentialId(credential.getId())
                    .map(d -> mapOf(
                            "agencyName", d.getAgencyName(),
                            "position", d.getPosition(),
                            "level", d.getLevel(),
                            "employeeId", d.getEmployeeId()
                    )).orElse(Map.of());
            case "senior" -> seniorDetailsRepository.findByUserCredentialId(credential.getId())
                    .map(d -> mapOf("dateOfBirth", formatDate(d.getDateOfBirth())))
                    .orElse(Map.of());
            case "nonprofit" -> nonprofitDetailsRepository.findByUserCredentialId(credential.getId())
                    .map(d -> mapOf(
                            "orgName", d.getOrgName(),
                            "ein", d.getEin(),
                            "position", d.getPosition(),
                            "orgType", d.getOrgType(),
                            "employmentStartDate", formatDate(d.getEmploymentStartDate())
                    )).orElse(Map.of());
            default -> Map.of();
        };
    }

    private Map<String, Object> toMap(UserCredential c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("credentialType", c.getCredentialType());
        m.put("status", c.getStatus().name().toLowerCase());
        m.put("fields", getCredentialFields(c));
        m.put("submittedAt", c.getStartedAt() != null ? c.getStartedAt().toString() : null);
        m.put("startedAt", c.getStartedAt() != null ? c.getStartedAt().toLocalDate().toString() : null);
        m.put("reviewedAt", c.getReviewedAt() != null ? c.getReviewedAt().toString() : null);
        m.put("verifiedAt", c.getVerifiedAt() != null ? c.getVerifiedAt().toLocalDate().toString() : null);
        m.put("reviewerNotes", c.getReviewerNotes());
        documentRepository.findTopByUser_IdAndDocumentTypeOrderByUploadedAtDesc(c.getUserId(), c.getCredentialType())
                .ifPresent(doc -> {
                    m.put("documentId", doc.getId());
                    m.put("documentName", doc.getOriginalFileName());
                    m.put("documentStatus", doc.getStatus().name().toLowerCase());
                });
        return m;
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void saveCredentialDetails(UserCredential credential, Map<String, String> fields) {
        switch (credential.getCredentialType()) {
            case "military" -> {
                MilitaryCredentialDetails details = militaryDetailsRepository.findByUserCredentialId(credential.getId())
                        .orElse(MilitaryCredentialDetails.builder().userCredentialId(credential.getId()).build());
                details.setBranch(trim(fields.get("branch")));
                details.setRank(trim(fields.get("rank")));
                details.setServiceStartDate(parseDate(fields.get("serviceStartDate")));
                details.setCurrentlyServing(parseBoolean(fields.get("currentlyServing")));
                details.setServiceEndDate(parseDate(fields.get("serviceEndDate")));
                details.setDischargeType(trim(fields.get("dischargeType")));
                militaryDetailsRepository.save(details);
            }
            case "student" -> {
                StudentCredentialDetails details = studentDetailsRepository.findByUserCredentialId(credential.getId())
                        .orElse(StudentCredentialDetails.builder().userCredentialId(credential.getId()).build());
                details.setSchoolName(trim(fields.get("schoolName")));
                details.setEnrollmentStatus(trim(fields.get("enrollmentStatus")));
                details.setMajor(trim(fields.get("major")));
                details.setStudentId(trim(fields.get("studentId")));
                details.setGraduationDate(trim(fields.get("graduationDate")));
                studentDetailsRepository.save(details);
            }
            case "first_responder" -> {
                FirstResponderCredentialDetails details = firstResponderDetailsRepository.findByUserCredentialId(credential.getId())
                        .orElse(FirstResponderCredentialDetails.builder().userCredentialId(credential.getId()).build());
                details.setAgencyName(trim(fields.get("agencyName")));
                details.setRole(trim(fields.get("role")));
                details.setBadgeNumber(trim(fields.get("badgeNumber")));
                details.setEmploymentStartDate(parseDate(fields.get("employmentStartDate")));
                firstResponderDetailsRepository.save(details);
            }
            case "teacher" -> {
                TeacherCredentialDetails details = teacherDetailsRepository.findByUserCredentialId(credential.getId())
                        .orElse(TeacherCredentialDetails.builder().userCredentialId(credential.getId()).build());
                details.setSchoolName(trim(fields.get("schoolName")));
                details.setTeachingLevel(trim(fields.get("teachingLevel")));
                details.setSubject(trim(fields.get("subject")));
                details.setEmployeeId(trim(fields.get("employeeId")));
                details.setEmploymentStartDate(parseDate(fields.get("employmentStartDate")));
                teacherDetailsRepository.save(details);
            }
            case "healthcare" -> {
                HealthcareCredentialDetails details = healthcareDetailsRepository.findByUserCredentialId(credential.getId())
                        .orElse(HealthcareCredentialDetails.builder().userCredentialId(credential.getId()).build());
                details.setLicenseType(trim(fields.get("licenseType")));
                details.setLicenseNumber(trim(fields.get("licenseNumber")));
                details.setIssuingState(trim(fields.get("issuingState")));
                details.setEmployer(trim(fields.get("employer")));
                healthcareDetailsRepository.save(details);
            }
            case "government" -> {
                GovernmentCredentialDetails details = governmentDetailsRepository.findByUserCredentialId(credential.getId())
                        .orElse(GovernmentCredentialDetails.builder().userCredentialId(credential.getId()).build());
                details.setAgencyName(trim(fields.get("agencyName")));
                details.setPosition(trim(fields.get("position")));
                details.setLevel(trim(fields.get("level")));
                details.setEmployeeId(trim(fields.get("employeeId")));
                governmentDetailsRepository.save(details);
            }
            case "senior" -> {
                SeniorCredentialDetails details = seniorDetailsRepository.findByUserCredentialId(credential.getId())
                        .orElse(SeniorCredentialDetails.builder().userCredentialId(credential.getId()).build());
                details.setDateOfBirth(parseDate(fields.get("dateOfBirth")));
                seniorDetailsRepository.save(details);
            }
            case "nonprofit" -> {
                NonprofitCredentialDetails details = nonprofitDetailsRepository.findByUserCredentialId(credential.getId())
                        .orElse(NonprofitCredentialDetails.builder().userCredentialId(credential.getId()).build());
                details.setOrgName(trim(fields.get("orgName")));
                details.setEin(trim(fields.get("ein")));
                details.setPosition(trim(fields.get("position")));
                details.setOrgType(trim(fields.get("orgType")));
                details.setEmploymentStartDate(parseDate(fields.get("employmentStartDate")));
                nonprofitDetailsRepository.save(details);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported credential type");
        }
    }

    private void upsertSupportingDocument(User user, String credentialType, MultipartFile file) {
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME_TYPES.contains(mime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG, WebP, and PDF files are accepted");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File size must not exceed 10 MB");
        }

        Document existing = documentRepository.findTopByUser_IdAndDocumentTypeOrderByUploadedAtDesc(user.getId(), credentialType)
                .orElse(null);

        try {
            if (existing != null) {
                storageService.delete(existing.getFilePath());
                String storedPath = storageService.store(
                        user.getId(), credentialType, existing.getId().intValue(), file.getOriginalFilename(), file);
                existing.setOriginalFileName(file.getOriginalFilename());
                existing.setFilePath(storedPath);
                existing.setFileSize(file.getSize());
                existing.setMimeType(mime);
                existing.setStatus(DocumentStatus.PENDING);
                documentRepository.save(existing);
                return;
            }

            int seq = documentRepository.countByUser_IdAndDocumentType(user.getId(), credentialType) + 1;
            String storedPath = storageService.store(user.getId(), credentialType, seq, file.getOriginalFilename(), file);
            Document doc = Document.builder()
                    .user(user)
                    .documentType(credentialType)
                    .originalFileName(file.getOriginalFilename())
                    .filePath(storedPath)
                    .fileSize(file.getSize())
                    .mimeType(mime)
                    .status(DocumentStatus.PENDING)
                    .build();
            documentRepository.save(doc);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store document");
        }
    }

    private LocalDate parseDate(String value) {
        String cleaned = trim(value);
        return cleaned == null ? null : LocalDate.parse(cleaned);
    }

    private Boolean parseBoolean(String value) {
        String cleaned = trim(value);
        return cleaned == null ? null : Boolean.parseBoolean(cleaned);
    }

    private String trim(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String formatDate(LocalDate value) {
        return value != null ? value.toString() : null;
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            if (value != null && !(value instanceof String s && s.isBlank())) {
                map.put(String.valueOf(pairs[i]), value);
            }
        }
        return map;
    }
}

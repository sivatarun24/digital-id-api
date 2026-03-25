package com.digitalid.api.service;

import com.digitalid.api.audit.AuditAction;
import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.*;
import com.digitalid.api.repositroy.*;
import com.digitalid.api.service.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CredentialService {

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
                              AuditLogService auditLogService) {
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

    public Map<String, Object> submitDocument(String username, String credentialType, MultipartFile file) {
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

        notificationService.create(user.getId(), "verification",
                "Supporting document received",
                capitalize(credentialType.replace("_", " ")) + " supporting document submitted and under review.");
        auditLogService.log(username, AuditAction.CREDENTIAL_VERIFY_STARTED,
                credentialType + " document submitted");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Document submitted successfully. Your credential is under review.");
        result.put("credential", toMap(cred));
        return result;
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

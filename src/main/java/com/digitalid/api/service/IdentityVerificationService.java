package com.digitalid.api.service;

import com.digitalid.api.audit.AuditAction;
import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.IdentityVerification;
import com.digitalid.api.controller.models.User;
import com.digitalid.api.controller.models.VerificationStatus;
import com.digitalid.api.repositroy.IdentityVerificationRepository;
import com.digitalid.api.repositroy.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class IdentityVerificationService {

    private final IdentityVerificationRepository repo;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    public IdentityVerificationService(IdentityVerificationRepository repo,
                                        UserRepository userRepository,
                                        NotificationService notificationService,
                                        AuditLogService auditLogService) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    public Map<String, Object> getStatus(String username) {
        User user = getUser(username);
        Optional<IdentityVerification> latest = repo.findTopByUserIdOrderBySubmittedAtDesc(user.getId());
        if (latest.isEmpty()) {
            return Map.of("status", "none");
        }
        IdentityVerification iv = latest.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", iv.getStatus().name().toLowerCase());
        result.put("idType", iv.getIdType());
        result.put("submittedAt", iv.getSubmittedAt().toLocalDate().toString());
        result.put("reviewedAt", iv.getReviewedAt() != null
                ? iv.getReviewedAt().toLocalDate().toString() : null);
        return result;
    }

    public Map<String, Object> submit(String username, String idType,
                                       MultipartFile frontFile,
                                       MultipartFile backFile,
                                       MultipartFile selfieFile) throws IOException {
        User user = getUser(username);

        if (idType == null || idType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID type is required");
        }
        if (frontFile == null || frontFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Front image is required");
        }
        if (selfieFile == null || selfieFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selfie is required");
        }

        Path dir = Paths.get(uploadsDir, "identity", String.valueOf(user.getId()));
        Files.createDirectories(dir);

        String frontPath = saveFile(dir, frontFile);
        String backPath  = backFile != null && !backFile.isEmpty() ? saveFile(dir, backFile) : null;
        String selfiePath = saveFile(dir, selfieFile);

        IdentityVerification iv = IdentityVerification.builder()
                .userId(user.getId())
                .idType(idType)
                .frontFilePath(frontPath)
                .backFilePath(backPath)
                .selfieFilePath(selfiePath)
                .status(VerificationStatus.VERIFIED) // auto-approve for demo
                .reviewedAt(LocalDateTime.now())
                .build();

        iv = repo.save(iv);

        notificationService.create(user.getId(), "verification",
                "Identity verified",
                "Congratulations! Your identity has been successfully verified. You now have full access.");
        auditLogService.log(username, AuditAction.IDENTITY_VERIFY_SUBMITTED, idType);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", iv.getStatus().name().toLowerCase());
        result.put("idType", iv.getIdType());
        result.put("submittedAt", iv.getSubmittedAt().toLocalDate().toString());
        result.put("reviewedAt", iv.getReviewedAt().toLocalDate().toString());
        return result;
    }

    public boolean isVerified(Long userId) {
        return repo.existsByUserIdAndStatus(userId, VerificationStatus.VERIFIED);
    }

    private String saveFile(Path dir, MultipartFile file) throws IOException {
        String ext = getExtension(file.getOriginalFilename());
        String name = UUID.randomUUID() + ext;
        Path dest = dir.resolve(name).toAbsolutePath();
        file.transferTo(dest.toFile());
        return dest.toString();
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}

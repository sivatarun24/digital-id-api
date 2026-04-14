package com.digitalid.api.service;

import com.digitalid.api.controller.models.InfoRequest;
import com.digitalid.api.repositroy.InfoRequestRepository;
import com.digitalid.api.repositroy.UserRepository;
import com.digitalid.api.service.storage.StorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class InfoRequestService {

    private static final Logger log = LoggerFactory.getLogger(InfoRequestService.class);

    private final InfoRequestRepository infoRequestRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    public InfoRequestService(InfoRequestRepository infoRequestRepository,
                              UserRepository userRepository,
                              ObjectMapper objectMapper,
                              StorageService storageService) {
        this.infoRequestRepository = infoRequestRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    public Map<String, Object> createRequest(Long userId, String note, String source, String requestedBy) {
        userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        InfoRequest req = InfoRequest.builder()
            .userId(userId)
            .note(note)
            .requestedBy(requestedBy != null ? requestedBy : "Admin")
            .source(source != null ? source : "user_detail")
            .resolved(false)
            .build();
        infoRequestRepository.save(req);
        return toMap(req);
    }

    public List<Map<String, Object>> getRequestsForUser(Long userId) {
        return infoRequestRepository.findByUserIdOrderByRequestedAtDesc(userId)
            .stream().map(this::toMap).toList();
    }

    public void deleteRequest(Long id) {
        InfoRequest req = infoRequestRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Info request not found"));
        deleteResponseFiles(req);
        infoRequestRepository.delete(req);
    }

    public void deleteByUserId(Long userId) {
        List<InfoRequest> requests = infoRequestRepository.findByUserIdOrderByRequestedAtDesc(userId);
        for (InfoRequest req : requests) {
            deleteResponseFiles(req);
            infoRequestRepository.delete(req);
        }
    }

    public List<Map<String, Object>> getPendingRequestsForUser(String username) {
        return userRepository.findByUsername(username).map(user ->
            infoRequestRepository.findByUserIdAndResolvedFalseOrderByRequestedAtDesc(user.getId())
                .stream().map(this::toMap).toList()
        ).orElse(Collections.emptyList());
    }

    public Map<String, Object> respondToRequest(String username, Long requestId,
                                                 String message, MultipartFile[] files) throws IOException {
        var user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        InfoRequest req = infoRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Info request not found"));
        if (!req.getUserId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        if (req.isResolved()) {
            throw new RuntimeException("Already responded");
        }

        List<Map<String, String>> filesMeta = new ArrayList<>();
        if (files != null && files.length > 0) {
            for (int i = 0; i < files.length; i++) {
                MultipartFile f = files[i];
                String orig = f.getOriginalFilename();
                String ext = "";
                if (orig != null && orig.contains(".")) {
                    ext = orig.substring(orig.lastIndexOf("."));
                }
                String filename = i + "_" + (orig != null ? orig.replaceAll("[^a-zA-Z0-9._-]", "_") : "file" + ext);
                String relPath = "info-responses/" + requestId + "/" + filename;
                String storedPath = storageService.storeWithPath(relPath, f.getContentType(), f);
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("name", orig != null ? orig : "file");
                meta.put("mimeType", f.getContentType() != null ? f.getContentType() : "application/octet-stream");
                meta.put("filename", filename);
                meta.put("storedPath", storedPath);
                filesMeta.add(meta);
            }
        }

        req.setResolved(true);
        req.setUserResponseMessage(message);
        req.setUserResponseFilesMeta(filesMeta.isEmpty() ? null : objectMapper.writeValueAsString(filesMeta));
        req.setRespondedAt(LocalDateTime.now());
        infoRequestRepository.save(req);
        return toMap(req);
    }

    public Resource getResponseFile(Long requestId, int fileIndex, String username, boolean isAdmin) {
        InfoRequest req = infoRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Info request not found"));
        if (!isAdmin) {
            var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            if (!req.getUserId().equals(user.getId())) {
                throw new RuntimeException("Access denied");
            }
        }
        List<Map<String, String>> meta = parseFilesMeta(req.getUserResponseFilesMeta());
        if (fileIndex < 0 || fileIndex >= meta.size()) {
            throw new RuntimeException("File not found");
        }
        Map<String, String> fileMeta = meta.get(fileIndex);
        String storedPath = fileMeta.get("storedPath");
        if (storedPath != null) {
            try {
                return storageService.load(storedPath);
            } catch (IOException e) {
                throw new RuntimeException("Could not load file", e);
            }
        }
        // backward compat: old records stored before cloud storage migration
        Path filePath = Paths.get(uploadsDir, "info-responses", String.valueOf(requestId), fileMeta.get("filename"));
        return new FileSystemResource(filePath);
    }

    public String getResponseFileMimeType(Long requestId, int fileIndex) {
        InfoRequest req = infoRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Info request not found"));
        List<Map<String, String>> meta = parseFilesMeta(req.getUserResponseFilesMeta());
        if (fileIndex < 0 || fileIndex >= meta.size()) return "application/octet-stream";
        return meta.get(fileIndex).getOrDefault("mimeType", "application/octet-stream");
    }

    public String getResponseFileName(Long requestId, int fileIndex) {
        InfoRequest req = infoRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Info request not found"));
        List<Map<String, String>> meta = parseFilesMeta(req.getUserResponseFilesMeta());
        if (fileIndex < 0 || fileIndex >= meta.size()) return "file";
        return meta.get(fileIndex).getOrDefault("name", "file");
    }

    private Map<String, Object> toMap(InfoRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", req.getId());
        m.put("userId", req.getUserId());
        m.put("note", req.getNote());
        m.put("requestedBy", req.getRequestedBy());
        m.put("requestedAt", req.getRequestedAt());
        m.put("source", req.getSource());
        m.put("resolved", req.isResolved());
        if (req.isResolved() && req.getRespondedAt() != null) {
            List<Map<String, String>> meta = parseFilesMeta(req.getUserResponseFilesMeta());
            List<Map<String, Object>> files = new ArrayList<>();
            for (int i = 0; i < meta.size(); i++) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("index", i);
                f.put("name", meta.get(i).getOrDefault("name", "file"));
                f.put("mimeType", meta.get(i).getOrDefault("mimeType", "application/octet-stream"));
                files.add(f);
            }
            Map<String, Object> userResponse = new LinkedHashMap<>();
            userResponse.put("message", req.getUserResponseMessage() != null ? req.getUserResponseMessage() : "");
            userResponse.put("files", files);
            userResponse.put("respondedAt", req.getRespondedAt());
            m.put("userResponse", userResponse);
        } else {
            m.put("userResponse", null);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseFilesMeta(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void deleteResponseFiles(InfoRequest req) {
        List<Map<String, String>> meta = parseFilesMeta(req.getUserResponseFilesMeta());
        for (Map<String, String> f : meta) {
            String storedPath = f.get("storedPath");
            if (storedPath != null) {
                storageService.delete(storedPath);
            } else {
                // backward compat: old records stored before cloud storage migration
                Path dir = Paths.get(uploadsDir, "info-responses", String.valueOf(req.getId()));
                try { Files.deleteIfExists(dir.resolve(f.get("filename"))); } catch (IOException e) {
                    log.warn("[InfoRequest] Failed to delete legacy file {}: {}", f.get("filename"), e.getMessage());
                }
            }
        }
    }
}

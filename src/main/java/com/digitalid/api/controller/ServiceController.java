package com.digitalid.api.controller;

import com.digitalid.api.service.ServiceConnectionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/services", produces = MediaType.APPLICATION_JSON_VALUE)
public class ServiceController {

    private final ServiceConnectionService serviceConnectionService;

    public ServiceController(ServiceConnectionService serviceConnectionService) {
        this.serviceConnectionService = serviceConnectionService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getServices(Authentication auth) {
        return ResponseEntity.ok(serviceConnectionService.getServices(auth.getName()));
    }

    @PostMapping("/{serviceSlug}/connect")
    public ResponseEntity<Map<String, Object>> connect(Authentication auth, @PathVariable String serviceSlug) {
        return ResponseEntity.ok(serviceConnectionService.connect(auth.getName(), serviceSlug));
    }

    @DeleteMapping("/{serviceSlug}/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(Authentication auth, @PathVariable String serviceSlug) {
        serviceConnectionService.disconnect(auth.getName(), serviceSlug);
        return ResponseEntity.ok(Map.of("message", "Service disconnected"));
    }
}

package com.digitalid.api.service.ocr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResult {
    private String fullName;
    private String institution;
    private String documentType;
    private String expiryDate;
    private double confidenceScore;
    private String rawText;
    private Map<String, String> extractedFields;
    private boolean success;
    private String errorMessage;
}

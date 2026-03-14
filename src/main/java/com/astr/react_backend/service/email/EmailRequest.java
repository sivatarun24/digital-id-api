package com.astr.react_backend.service.email;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class EmailRequest {
    private String to;
    private String subject;
    private String body;
    private EmailType emailType;
    private boolean html;
    private Map<String, String> templateVariables;
}

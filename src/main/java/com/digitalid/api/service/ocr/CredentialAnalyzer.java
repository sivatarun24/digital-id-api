package com.digitalid.api.service.ocr;

import org.springframework.stereotype.Component;

@Component
public class CredentialAnalyzer {

    private static final double MIN_MATCH_SCORE = 0.6;

    /**
     * Analyzes raw OCR text to verify user information.
     * 
     * @param rawText        Text extracted from the document
     * @param expectedName   Full name expected from user profile
     * @param credentialType The type of credential being verified
     * @return AnalyzeResult with confidence score and matches
     */
    public AnalyzeResult analyze(String rawText, String expectedName, String credentialType) {
        if (rawText == null || rawText.isBlank()) {
            return new AnalyzeResult(0.0, false, "No legible text could be found on the document. Please ensure it is clear and well-lit.");
        }

        String normalizedText = rawText.toLowerCase();
        String normalizedExpected = expectedName.toLowerCase();

        // 1. Check for Name Match
        double nameScore = calculatePartialMatchScore(normalizedText, normalizedExpected);

        // 2. Check for Credential Type Keywords
        boolean typeMatch = checkTypeMatch(normalizedText, credentialType);

        double totalConfidence = nameScore;
        if (!typeMatch)
            totalConfidence *= 0.5; // Penalty for missing type markers

        boolean verified = totalConfidence >= MIN_MATCH_SCORE;
        String note = verified ? "Verified"
                : (nameScore < MIN_MATCH_SCORE ? "The name on the document does not match your profile name." 
                                              : "The uploaded document does not appear to match the selected ID type.");

        return new AnalyzeResult(totalConfidence, verified, note);
    }

    private double calculatePartialMatchScore(String text, String target) {
        if (text == null || target == null || target.isBlank()) return 0.0;
        
        String cleanText = text.replaceAll("[^a-zA-Z0-9\\s]", " ").toLowerCase();
        String cleanTarget = target.toLowerCase();
        
        // Exact match check
        if (cleanText.contains(cleanTarget)) return 1.0;

        String[] ocrWords = cleanText.split("\\s+");
        String[] targetParts = cleanTarget.split("\\s+");
        
        double totalScore = 0.0;
        for (String targetPart : targetParts) {
            if (targetPart.length() <= 2) continue;
            
            double bestPartScore = 0.0;
            for (String ocrWord : ocrWords) {
                if (ocrWord.length() < 3) continue;
                
                // Check if targetPart is contained in the (possibly longer) ocrWord
                // or if it's very close (Levenshtein)
                double score = 0.0;
                if (ocrWord.contains(targetPart)) {
                    score = (double) targetPart.length() / ocrWord.length();
                } else {
                    int distance = levenshteinDistance(targetPart, ocrWord);
                    int maxLength = Math.max(targetPart.length(), ocrWord.length());
                    score = 1.0 - ((double) distance / maxLength);
                }
                
                if (score > bestPartScore) bestPartScore = score;
            }
            totalScore += bestPartScore;
        }

        return totalScore / targetParts.length;
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }

    private boolean checkTypeMatch(String text, String type) {
        return switch (type) {
            case "student" -> text.contains("student") || text.contains("university") || text.contains("college")
                    || text.contains("institute");
            case "military" -> text.contains("military") || text.contains("army") || text.contains("navy")
                    || text.contains("force") || text.contains("defense");
            case "healthcare" -> text.contains("healthcare") || text.contains("medical") || text.contains("hospital")
                    || text.contains("clinic") || text.contains("nurse") || text.contains("doctor")
                    || text.contains("physician") || text.contains("health");
            case "teacher" -> text.contains("teacher") || text.contains("school") || text.contains("education")
                    || text.contains("faculty") || text.contains("professor") || text.contains("instructor")
                    || text.contains("academic");
            case "first_responder" -> text.contains("police") || text.contains("fire") || text.contains("ems")
                    || text.contains("emergency") || text.contains("ambulance") || text.contains("paramedic")
                    || text.contains("responder") || text.contains("sheriff");
            case "government" -> text.contains("government") || text.contains("federal") || text.contains("state")
                    || text.contains("county") || text.contains("municipal") || text.contains("department")
                    || text.contains("agency") || text.contains("official");
            case "nonprofit" -> text.contains("nonprofit") || text.contains("non-profit") || text.contains("charity")
                    || text.contains("foundation") || text.contains("organization") || text.contains("501(c)(3)");
            case "senior" -> text.contains("identification") || text.contains("card") || text.contains("license")
                    || text.contains("state") || text.contains("birth");
            case "drivers_license" -> text.contains("driver") || text.contains("license") || text.contains("permit")
                    || text.contains("operator");
            case "passport", "passport_card" -> text.contains("passport") || text.contains("republic")
                    || text.contains("united states") || text.contains("travel");
            case "state_id" -> text.contains("state") || text.contains("identification") || text.contains("card")
                    || text.contains("residence");
            case "military_id" -> text.contains("military") || text.contains("defense") || text.contains("department")
                    || text.contains("armed forces");
            default -> false; // Stricter validation for unknown types
        };
    }

    public record AnalyzeResult(double confidence, boolean isMatch, String message) {
    }
}

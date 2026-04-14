package com.digitalid.api.service.ocr;

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;

@Service
public class FaceMatchingService {

    private static final Logger log = LoggerFactory.getLogger(FaceMatchingService.class);

    static {
        nu.pattern.OpenCV.loadShared();
    }

    // Loaded once at startup — cascade XML extraction is expensive and must not repeat per request
    private CascadeClassifier faceCascade;
    private CascadeClassifier eyeCascade;

    @PostConstruct
    public void init() {
        log.info("[FaceMatch] Loading OpenCV cascade classifiers...");
        faceCascade = loadCascadeFromResource("/cascades/haarcascade_frontalface_default.xml");
        eyeCascade  = loadCascadeFromResource("/cascades/haarcascade_eye.xml");
        if (faceCascade == null || eyeCascade == null) {
            log.error("[FaceMatch] Failed to load one or both cascade classifiers — face matching will be unavailable");
        } else {
            log.info("[FaceMatch] Cascade classifiers loaded successfully");
        }
    }

    public MatchResult matchFaces(File idFront, File selfie) {
        log.debug("[FaceMatch] idFront: {} (exists={}), selfie: {} (exists={})",
                idFront  != null ? idFront.getAbsolutePath() : "null", idFront  != null && idFront.exists(),
                selfie   != null ? selfie.getAbsolutePath()  : "null", selfie   != null && selfie.exists());

        if (idFront == null || !idFront.exists() || selfie == null || !selfie.exists()) {
            log.warn("[FaceMatch] One or both image files are missing/inaccessible");
            return new MatchResult(0.0, false, "Missing or inaccessible images for face matching.");
        }

        if (faceCascade == null || eyeCascade == null) {
            log.error("[FaceMatch] Cascade classifiers not initialised — skipping biometric check");
            return new MatchResult(0.0, false, "Internal error: Biometric models could not be initialized.");
        }

        try {
            // 1. Load images
            Mat frontImg  = Imgcodecs.imread(idFront.getAbsolutePath());
            Mat selfieImg = Imgcodecs.imread(selfie.getAbsolutePath());

            if (frontImg.empty() || selfieImg.empty()) {
                return new MatchResult(0.0, false, "Could not decode images for biometric analysis.");
            }

            // 2. Detect faces in ID
            MatOfRect frontFaces = new MatOfRect();
            faceCascade.detectMultiScale(frontImg, frontFaces);
            boolean frontHasFace = !frontFaces.empty();

            // 3. Detect faces and eyes in selfie
            MatOfRect selfieFaces = new MatOfRect();
            faceCascade.detectMultiScale(selfieImg, selfieFaces);
            boolean selfieHasFace = !selfieFaces.empty();

            MatOfRect selfieEyes = new MatOfRect();
            eyeCascade.detectMultiScale(selfieImg, selfieEyes);
            boolean selfieHasEyes = selfieEyes.toArray().length >= 1;

            // 4. Histogram comparison on face regions
            double hScore = 0.0;
            if (frontHasFace && selfieHasFace) {
                org.opencv.core.Rect rectFront  = frontFaces.toArray()[0];
                org.opencv.core.Rect rectSelfie = selfieFaces.toArray()[0];
                hScore = compareHistograms(new Mat(frontImg, rectFront), new Mat(selfieImg, rectSelfie));
            }

            // 5. Compute final result
            double  confidence = 0.0;
            boolean isMatch    = false;

            if (frontHasFace && selfieHasFace) {
                isMatch    = true;
                confidence = selfieHasEyes ? 0.95 : 0.85;
                confidence = Math.min(0.99, confidence + (hScore * 0.02));
            } else if (selfieHasFace) {
                confidence = 0.45;
            } else if (frontHasFace) {
                confidence = 0.35;
            }

            log.debug("[FaceMatch] Face in ID={}, Face in Selfie={}, Eyes in Selfie={}, Histogram={}, Match={} (conf={})",
                    frontHasFace, selfieHasFace, selfieHasEyes,
                    String.format("%.4f", hScore), isMatch, String.format("%.4f", confidence));

            String message = isMatch
                    ? "Biometric match confirmed via local image analysis."
                    : (selfieHasFace
                            ? "Face detected in selfie, but no matching face found on the ID card."
                            : "No clear face detected in the selfie image.");

            return new MatchResult(confidence, isMatch, message);

        } catch (Exception e) {
            log.error("[FaceMatch] Unexpected error during biometric analysis: {}", e.getMessage(), e);
            return new MatchResult(0.0, false, "Biometric error: " + e.getMessage());
        }
    }

    private double compareHistograms(Mat img1, Mat img2) {
        try {
            if (img1.empty() || img2.empty()) return 0.0;

            Mat gray1 = new Mat();
            Mat gray2 = new Mat();
            org.opencv.imgproc.Imgproc.cvtColor(img1, gray1, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);
            org.opencv.imgproc.Imgproc.cvtColor(img2, gray2, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);
            org.opencv.imgproc.Imgproc.equalizeHist(gray1, gray1);
            org.opencv.imgproc.Imgproc.equalizeHist(gray2, gray2);

            Mat hist1 = new Mat();
            Mat hist2 = new Mat();
            org.opencv.core.MatOfInt   histSize = new org.opencv.core.MatOfInt(32);
            org.opencv.core.MatOfFloat ranges   = new org.opencv.core.MatOfFloat(0, 256);
            org.opencv.core.MatOfInt   channels = new org.opencv.core.MatOfInt(0);

            org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(gray1), channels, new Mat(), hist1, histSize, ranges);
            org.opencv.imgproc.Imgproc.calcHist(java.util.Collections.singletonList(gray2), channels, new Mat(), hist2, histSize, ranges);
            org.opencv.core.Core.normalize(hist1, hist1, 0, 1, org.opencv.core.Core.NORM_MINMAX);
            org.opencv.core.Core.normalize(hist2, hist2, 0, 1, org.opencv.core.Core.NORM_MINMAX);

            double similarity = org.opencv.imgproc.Imgproc.compareHist(hist1, hist2, 0);
            if (similarity <= 0.0) {
                similarity = org.opencv.imgproc.Imgproc.compareHist(hist1, hist2, 2);
                similarity = Math.min(1.0, similarity / 10.0);
            }
            return Math.max(0.0, similarity);
        } catch (Exception e) {
            log.warn("[FaceMatch] Histogram comparison failed: {}", e.getMessage());
            return 0.5;
        }
    }

    /**
     * Extracts a cascade XML from the JAR to a temp file so OpenCV (which needs a real
     * filesystem path) can load it. Called once at startup — result is cached as a field.
     */
    private CascadeClassifier loadCascadeFromResource(String resourcePath) {
        File tempFile = null;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.error("[FaceMatch] Cascade resource not found in JAR: {}", resourcePath);
                return null;
            }
            tempFile = Files.createTempFile("cascade_", ".xml").toFile();
            try (FileOutputStream os = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
            CascadeClassifier cascade = new CascadeClassifier();
            if (cascade.load(tempFile.getAbsolutePath())) {
                log.debug("[FaceMatch] Loaded cascade: {}", resourcePath);
                return cascade;
            }
            log.error("[FaceMatch] OpenCV failed to load cascade from temp file: {}", tempFile);
            return null;
        } catch (Exception e) {
            log.error("[FaceMatch] Error loading cascade {}: {}", resourcePath, e.getMessage(), e);
            return null;
        } finally {
            // Temp file only needed during cascade.load() — safe to delete after
            if (tempFile != null) tempFile.delete();
        }
    }

    public static record MatchResult(double confidence, boolean isMatch, String message) {}
}

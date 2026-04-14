package com.digitalid.api.service.ocr;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class TesseractOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrService.class);

    @Value("${ocr.tessdata.path:/opt/homebrew/share/tessdata}")
    private String tessdataPath;

    private ITesseract tesseract;

    @PostConstruct
    public void init() {
        // Force JAI ImageIO SPI registration — Spring Boot fat-JAR nests JARs inside JARs,
        // which prevents the automatic classpath SPI scan from finding JAI readers.
        // scanForPlugins() uses the current thread's context classloader (Spring Boot's
        // LaunchedURLClassLoader) so it can reach nested JAR resources.
        ImageIO.scanForPlugins();
        log.info("[OCR] ImageIO readers registered: {}", java.util.Arrays.toString(ImageIO.getReaderFormatNames()));

        log.info("[OCR] Initializing Tesseract with tessdata path: {}", tessdataPath);
        tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage("eng");
        log.info("[OCR] Tesseract ready (language=eng)");
    }

    @Override
    public OcrResult extractText(MultipartFile file) {
        log.debug("[OCR] extractText from MultipartFile: name={}, size={} bytes, type={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("ocr_", "_" + file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            log.debug("[OCR] Written to temp file: {}", tempFile);
            return extractText(tempFile.toFile());
        } catch (IOException e) {
            log.error("[OCR] Failed to prepare file for OCR: {}", e.getMessage());
            return OcrResult.builder()
                    .success(false)
                    .errorMessage("Failed to process file: " + e.getMessage())
                    .build();
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
        }
    }

    @Override
    public OcrResult extractText(File file) {
        log.debug("[OCR] Running Tesseract on file: {} (exists={}, size={} bytes)",
                file.getAbsolutePath(), file.exists(), file.length());
        try {
            // Decode the image with ImageIO first (now backed by JAI plugins) and pass a
            // BufferedImage to Tess4j.  This bypasses Tess4j's own format detection logic
            // which throws "Unsupported image format" for progressive/CMYK JPEGs and other
            // edge cases even when the underlying reader is available.
            BufferedImage image = ImageIO.read(file);
            String result;
            if (image != null) {
                log.debug("[OCR] ImageIO decoded image — {}x{}, type={}", image.getWidth(), image.getHeight(), image.getType());
                result = tesseract.doOCR(image);
            } else {
                // ImageIO returned null (format still not recognised) — fall back to Tess4j's
                // native Leptonica-based file reader which handles TIFF/BMP directly.
                log.debug("[OCR] ImageIO returned null — falling back to native Leptonica reader");
                result = tesseract.doOCR(file);
            }
            int charCount = result != null ? result.length() : 0;
            log.debug("[OCR] Success — extracted {} chars: [{}]",
                    charCount, result != null ? result.replace("\n", " | ").substring(0, Math.min(charCount, 200)) : "");
            return OcrResult.builder()
                    .rawText(result)
                    .success(true)
                    .build();
        } catch (TesseractException | IOException e) {
            log.warn("[OCR] Tesseract failed on {}: {}", file.getName(), e.getMessage());
            return OcrResult.builder()
                    .success(false)
                    .errorMessage("OCR Library error: " + e.getMessage())
                    .build();
        }
    }
}

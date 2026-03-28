package com.digitalid.api.service.ocr;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class TesseractOcrService implements OcrService {

    @Value("${ocr.tessdata.path:/opt/homebrew/share/tessdata}")
    private String tessdataPath;

    private ITesseract tesseract;

    @PostConstruct
    public void init() {
        tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage("eng");
    }

    @Override
    public OcrResult extractText(MultipartFile file) {
        try {
            Path tempFile = Files.createTempFile("ocr_", "_" + file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            OcrResult result = extractText(tempFile.toFile());
            Files.deleteIfExists(tempFile);
            return result;
        } catch (IOException e) {
            return OcrResult.builder()
                    .success(false)
                    .errorMessage("Failed to process file: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public OcrResult extractText(File file) {
        try {
            String result = tesseract.doOCR(file);
            return OcrResult.builder()
                    .rawText(result)
                    .success(true)
                    .build();
        } catch (TesseractException e) {
            return OcrResult.builder()
                    .success(false)
                    .errorMessage("OCR Library error: " + e.getMessage())
                    .build();
        }
    }
}

package com.digitalid.api.service.ocr;

import org.springframework.web.multipart.MultipartFile;
import java.io.File;

public interface OcrService {
    /**
     * Extracts text and structured data from a document image or PDF.
     * @param file The uploaded multipart file
     * @return OcrResult containing extracted fields and confidence
     */
    OcrResult extractText(MultipartFile file);

    /**
     * Extracts text from a local file.
     * @param file Local file on disk
     * @return OcrResult
     */
    OcrResult extractText(File file);
}

package de.skillkiller.documentdbackend.task;

import de.skillkiller.documentdbackend.entity.Document;
import de.skillkiller.documentdbackend.search.DocumentSearch;
import de.skillkiller.documentdbackend.util.FileUtil;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Optional;


public class PDFOCR implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(PDFOCR.class);
    private final Document document;
    private final DocumentSearch documentSearch;
    private final FileUtil fileUtil;
    private final String dataPath;
    private final String language;

    public PDFOCR(Document document, DocumentSearch documentSearch, FileUtil fileUtil, String dataPath, String language) {
        this.document = document;
        this.documentSearch = documentSearch;
        this.fileUtil = fileUtil;
        this.dataPath = dataPath;
        this.language = language;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("PDFOCR-" + document.getId());
        logger.info("Start PDF OCR for document " + document.getId());
        if (document.getTextContent() != null) {
            logger.warn("Document " + document.getId() + " already have text content! This task override it!");
        }
        File targetFile = fileUtil.getFile(document);

        try (PDDocument pdfDocument = PDDocument.load(targetFile)) {

            PDFRenderer pr = new PDFRenderer(pdfDocument);
            Tesseract tesseract = new Tesseract();
            tesseract.setLanguage(language);
            tesseract.setDatapath(dataPath);
            StringBuilder stringBuilder = new StringBuilder();

            for (int i = 0; i < pdfDocument.getNumberOfPages(); i++) {
                BufferedImage bi = pr.renderImageWithDPI(i, 600);
                stringBuilder.append(tesseract.doOCR(bi)).append("\n");
            }

            String content = stringBuilder.toString();

            Optional<Document> optionalDocument = documentSearch.getDocumentById(document.getId());
            if (optionalDocument.isPresent()) {
                Document document = optionalDocument.get();
                if (!content.isBlank()) {
                    document.setTextContent(content);
                    documentSearch.createOrReplaceDocument(document);
                    logger.info("Updated document " + this.document.getId() + " with text content.");
                } else {
                    logger.debug("PDF OCR dont found content for document " + this.document.getId());
                }
            }
        } catch (IOException | TesseractException e) {
            e.printStackTrace();
        }

    }
}

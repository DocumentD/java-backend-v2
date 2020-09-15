package de.skillkiller.documentdbackend.task;

import de.skillkiller.documentdbackend.entity.Document;
import de.skillkiller.documentdbackend.search.MeliSearch;
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
    private final String documentId;
    private final MeliSearch meliSearch;
    private final FileUtil fileUtil;

    public PDFOCR(String documentId, MeliSearch meliSearch, FileUtil fileUtil) {
        this.documentId = documentId;
        this.meliSearch = meliSearch;
        this.fileUtil = fileUtil;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("PDFOCR-" + documentId);
        logger.info("Start PDF OCR for document " + documentId);
        Optional<Document> optionalDocument = meliSearch.getDocumentById(documentId);
        if (optionalDocument.isPresent()) {
            if (optionalDocument.get().getTextContent() != null) {
                logger.warn("Document " + documentId + " already have text content! This task override it!");
            }
            File targetFile = fileUtil.getFile(optionalDocument.get());

            try (PDDocument pdfDocument = PDDocument.load(targetFile)) {

                PDFRenderer pr = new PDFRenderer(pdfDocument);
                Tesseract tesseract = new Tesseract();
                tesseract.setLanguage("deu");
                tesseract.setDatapath("D:\\tessdata_best-master"); //TODO Auslagern

                StringBuilder stringBuilder = new StringBuilder();

                for (int i = 0; i < pdfDocument.getNumberOfPages(); i++) {
                    BufferedImage bi = pr.renderImageWithDPI(i, 600);
                    stringBuilder.append(tesseract.doOCR(bi)).append("\n");
                }

                String content = stringBuilder.toString();

                optionalDocument = meliSearch.getDocumentById(documentId);
                if (optionalDocument.isPresent()) {
                    Document document = optionalDocument.get();
                    if (!content.isBlank()) {
                        document.setTextContent(content);
                        meliSearch.createOrReplaceDocument(document);
                        logger.info("Updated document " + documentId + " with text content.");
                    } else {
                        logger.debug("PDF OCR dont found content for document " + documentId);
                    }
                }
            } catch (IOException | TesseractException e) {
                e.printStackTrace();
            }

        } else {
            logger.info("Failed PDF OCR for " + documentId + ". Document not anymore existing!");
        }
    }
}

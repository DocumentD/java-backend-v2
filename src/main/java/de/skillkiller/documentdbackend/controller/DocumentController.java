package de.skillkiller.documentdbackend.controller;

import de.skillkiller.documentdbackend.entity.AccessToken;
import de.skillkiller.documentdbackend.entity.Document;
import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.entity.UserDetailsHolder;
import de.skillkiller.documentdbackend.entity.http.SearchResponse;
import de.skillkiller.documentdbackend.search.MeliSearch;
import de.skillkiller.documentdbackend.service.AccessTokenService;
import de.skillkiller.documentdbackend.task.PDFOCR;
import de.skillkiller.documentdbackend.util.FileUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("document")
@CrossOrigin(methods = {RequestMethod.POST, RequestMethod.GET, RequestMethod.DELETE}, origins = {"*"})
public class DocumentController {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(3);
    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);
    private final MeliSearch meliSearch;
    private final FileUtil fileUtil;
    private final AccessTokenService accessTokenService;

    public DocumentController(MeliSearch meliSearch, FileUtil fileUtil, AccessTokenService accessTokenService) {
        this.meliSearch = meliSearch;
        this.fileUtil = fileUtil;
        this.accessTokenService = accessTokenService;
    }

    @GetMapping("gettop")
    public List<?> getTopDocuments(Authentication authentication) {
        User authenticatedUser = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();
        SearchResponse searchResponse = meliSearch.searchForTopDocumentsInUserScope(authenticatedUser.getId());
        return searchResponse.getHits();
    }

    @GetMapping("search")
    public List<?> searchForDocuments(Authentication authentication, @RequestParam("search") String search) {
        User authenticatedUser = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();
        SearchResponse searchResponse = meliSearch.searchForDocumentInUserScope(authenticatedUser.getId(), search);
        return searchResponse.getHits();
    }

    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public @ResponseBody
    ResponseEntity<Document> handleFileUpload(Authentication authentication, @RequestParam("file") MultipartFile multipartFile) {
        User authenticatedUser = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();
        if (!multipartFile.isEmpty()) {
            Document document = new Document();
            try {
                byte[] bytes = multipartFile.getBytes();
                File tempFile = File.createTempFile(authenticatedUser.getId(), "upload");
                FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
                BufferedOutputStream stream =
                        new BufferedOutputStream(fileOutputStream);
                stream.write(bytes);
                stream.close();
                fileOutputStream.close();
                PDDocument pdfDocument = PDDocument.load(tempFile);

                document.setUserId(authenticatedUser.getId());
                document.setPages(pdfDocument.getNumberOfPages());
                document.setDocumentDate(new Date());
                document.setFilename("IN PROGRESS");
                document.setPdfTitle(pdfDocument.getDocumentInformation().getTitle());
                // TODO Check for duplicates
                document.setId(DigestUtils.sha1Hex(System.currentTimeMillis() + "#" + authenticatedUser.getId() + "#" + document.getPdfTitle()));
                String title = multipartFile.getOriginalFilename();
                if (title != null && title.contains(".")) {
                    title = title.substring(0, title.lastIndexOf("."));
                }
                document.setTitle(title);

                PDFTextStripper pdfTextStripper = new PDFTextStripper();
                String content = pdfTextStripper.getText(pdfDocument);
                if (!content.isBlank()) {
                    document.setTextContent(content);
                }
                pdfDocument.close();

                meliSearch.createOrReplaceDocument(document);

                String fileName = document.getId() + "-" + multipartFile.getOriginalFilename();
                if (!fileName.endsWith(".pdf")) fileName = fileName + ".pdf";

                document.setFilename(fileName);
                File saveDir = fileUtil.getBaseDirFromUser(authenticatedUser.getId());
                saveDir.mkdirs();
                File targetFile = fileUtil.getFile(document);
                Files.move(tempFile.toPath(), targetFile.toPath());

                meliSearch.createOrReplaceDocument(document);

                if (document.getTextContent() == null) {
                    executorService.execute(new PDFOCR(document.getId(), meliSearch, fileUtil));
                }
                logger.debug("Uploaded and created Document " + document.getId());
                return ResponseEntity.ok(document);
            } catch (Exception e) {
                logger.error("Something go wrong by upload", e);
                meliSearch.deleteDocument(document.getId());
                return ResponseEntity.status(500).build();
            }
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("update")
    public ResponseEntity<Document> updateDocument(Authentication authentication, @RequestBody Document receivedDocument) {
        User authenticatedUser = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();
        logger.debug("Received modify Document Request");
        if (receivedDocument.getTitle() == null || receivedDocument.getTitle().length() > 64 || receivedDocument.getTitle().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        if (receivedDocument.getId() == null || receivedDocument.getDocumentDate() == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<Document> optionalDocument = meliSearch.getDocumentByIdAndUserId(receivedDocument.getId(), authenticatedUser.getId());
        if (optionalDocument.isPresent()) {
            Document document = optionalDocument.get();

            if (!document.getUserId().equals(authenticatedUser.getId())) return ResponseEntity.badRequest().build();

            //Search for company und category difference
            boolean userUpdates = false;

            if ((receivedDocument.getCompany() == null && document.getCompany() != null) ||
                    (receivedDocument.getCompany() != null && document.getCompany() == null))
                if (!(receivedDocument.getCompany() == null ? document.getCompany() == null : receivedDocument.getCompany().equals(document.getCompany()))) {
                    SearchResponse searchResponse = meliSearch.getDocumentsWithCompanyFilterInUserScope(authenticatedUser.getId(), document.getCompany());
                    Set<String> companies = authenticatedUser.getCompanies();
                    if (companies == null) companies = new HashSet<>();
                    if (searchResponse.getHits().size() <= 1) {
                        if (document.getCompany() != null) companies.remove(document.getCompany());
                        authenticatedUser.setCompanies(companies);
                        userUpdates = true;
                    }

                    if (receivedDocument.getCompany() != null && !companies.contains(receivedDocument.getCompany())) {
                        companies.add(receivedDocument.getCompany());
                        userUpdates = true;
                    }
                }

            if (!(receivedDocument.getCategory() == null ? document.getCategory() == null : receivedDocument.getCategory().equals(document.getCategory()))) {
                SearchResponse searchResponse = meliSearch.getDocumentsWithCategoryFilterInUserScope(authenticatedUser.getId(), document.getCategory());
                Set<String> categories = authenticatedUser.getCategories();
                if (categories == null) categories = new HashSet<>();
                if (searchResponse.getHits().size() <= 1) {
                    if (document.getCategory() != null) categories.remove(document.getCategory());
                    authenticatedUser.setCompanies(categories);
                    userUpdates = true;
                }

                if (receivedDocument.getCategory() != null && !categories.contains(receivedDocument.getCategory())) {
                    categories.add(receivedDocument.getCategory());
                    userUpdates = true;
                }
            }

            if (userUpdates) {
                meliSearch.createOrReplaceUser(authenticatedUser);
            }

            meliSearch.createOrReplaceDocument(receivedDocument);
            logger.debug("Updated document " + document.getId());
            return ResponseEntity.ok(document);
        } else return ResponseEntity.notFound().build();

    }

    @DeleteMapping("/delete/{id:[\\d\\w]+}")
    public ResponseEntity<Void> deleteDocument(Authentication authentication, @PathVariable("id") String documentId) {
        User authenticatedUser = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();
        logger.debug("Received delete Document Request");
        Optional<Document> optionalDocument = meliSearch.getDocumentByIdAndUserId(documentId, authenticatedUser.getId());
        if (optionalDocument.isPresent()) {
            Document document = optionalDocument.get();
            boolean delete = fileUtil.getFile(document).delete();
            if (!delete) logger.warn("Delete from document file " + document.getId() + " failed!");
            meliSearch.deleteDocument(document.getId());
            logger.debug("Delete document " + documentId);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/accesToken/{id:[\\d\\w]+}")
    public ResponseEntity<AccessToken> generateAccessToken(Authentication authentication, @PathVariable("id") String documentId) {
        User authenticatedUser = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();
        Optional<Document> optionalDocument = meliSearch.getDocumentByIdAndUserId(documentId, authenticatedUser.getId());
        if (optionalDocument.isPresent()) {
            AccessToken accessToken = new AccessToken();
            accessToken.setDocumentId(optionalDocument.get().getId());

            Calendar date = Calendar.getInstance();
            long t = date.getTimeInMillis();
            Date afterAddingTenMin = new Date(t + (10 * 60000));
            accessToken.setExpire(afterAddingTenMin);
            accessTokenService.putToken(accessToken);
            return ResponseEntity.ok(accessToken);
        } else return ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/open/{token:[\\d\\w]+}/{.*}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> openDocument(@PathVariable("token") String token) {
        Optional<AccessToken> documentAccessTokenOptional = accessTokenService.isValidAndGet(token);
        if (documentAccessTokenOptional.isPresent()) {
            AccessToken accessToken = documentAccessTokenOptional.get();
            Optional<Document> documentOptional = meliSearch.getDocumentById(accessToken.getDocumentId());
            if (documentOptional.isPresent()) {
                File file = fileUtil.getFile(documentOptional.get());
                if (file.exists() && file.isFile()) {
                    try {
                        byte[] bytes = FileUtils.readFileToByteArray(file);
                        return ResponseEntity.ok(bytes);
                    } catch (IOException e) {
                        logger.error("Error by getting file", e);
                        return ResponseEntity.notFound().build();
                    }
                } else return ResponseEntity.notFound().build();
            } else return ResponseEntity.notFound().build();
        } else return ResponseEntity.status(403).build();
    }

}

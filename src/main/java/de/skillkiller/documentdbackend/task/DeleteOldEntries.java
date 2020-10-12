package de.skillkiller.documentdbackend.task;

import de.skillkiller.documentdbackend.controller.DocumentController;
import de.skillkiller.documentdbackend.entity.Document;
import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.search.DocumentSearch;
import de.skillkiller.documentdbackend.search.UserSearch;
import de.skillkiller.documentdbackend.service.DatabaseLockService;
import de.skillkiller.documentdbackend.service.UserDetailsService;
import de.skillkiller.documentdbackend.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class DeleteOldEntries {

    private static final Logger logger = LoggerFactory.getLogger(DeleteOldEntries.class);
    private final DocumentSearch documentSearch;
    private final DocumentController documentController;
    private final UserSearch userSearch;
    private final UserDetailsService userDetailsService;
    private final DatabaseLockService databaseLockService;
    private final FileUtil fileUtil;

    public DeleteOldEntries(DocumentSearch documentSearch, DocumentController documentController, UserSearch userSearch, UserDetailsService userDetailsService, DatabaseLockService databaseLockService, FileUtil fileUtil) {
        this.documentSearch = documentSearch;
        this.documentController = documentController;
        this.userSearch = userSearch;
        this.userDetailsService = userDetailsService;
        this.databaseLockService = databaseLockService;
        this.fileUtil = fileUtil;
    }


    @Scheduled(fixedRate = 24 * 60 * 60 * 1000, initialDelay = 1 * 1000)
    public void getAllData() {
        databaseLockService.lockNewWriteOperations();

        logger.info("Wait for finishing currently running write operations");
        try {
            databaseLockService.waitForAllWriteOperationsToFinish(TimeUnit.SECONDS.toMillis(30));
            logger.info("All write operations completed");
            logger.info("Wait for process meilisearch updates");
            int tries = 0;
            boolean processed;

            do {
                processed = userSearch.hasAllUpdatesProcessed();
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                tries++;
                if (tries >= 5) throw new RuntimeException("Exceeded maximum retries for waiting for user updates");
            } while (!processed);

            tries = 0;
            do {
                processed = documentSearch.hasAllUpdatesProcessed();
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                tries++;
                if (tries >= 5) throw new RuntimeException("Exceeded maximum retries for waiting for document updates");
            } while (!processed);
            logger.info("All meilisearch update operations completed");

            logger.debug("Start dumping data");
            HashMap<String, User> users = new LinkedHashMap<>();
            List<Document> documents = new LinkedList<>();
            List<File> checkedFiles = new ArrayList<>();

            List<User> getUser;
            int offset = 0;
            int limit = 100;
            do {
                getUser = userSearch.getUsers(offset, limit);
                offset += limit;
                for (User user : getUser) {
                    users.put(user.getId(), user);
                }
            } while (getUser.size() == limit);

            List<Document> getDocument;
            offset = 0;
            do {
                getDocument = documentSearch.getDocuments(offset, limit);
                offset += limit;
                documents.addAll(getDocument);
            } while (getDocument.size() == limit);

            logger.info("Collected all data from Meilisearch");

            //TODO Check User autocompletion

            // Check all documents for file and user
            for (Document document : documents) {
                File file = fileUtil.getFile(document);
                boolean needDelete = false;
                if (file.exists()) {
                    if (file.isFile()) {
                        if (users.containsKey(document.getUserId())) {
                            checkedFiles.add(file);
                        } else {
                            needDelete = true;
                            logger.warn("Document" + document.getId() + " exists but user does not");
                        }
                    } else {
                        needDelete = true;

                        logger.warn("Document " + document.getId() + " linked file is not a file");
                    }
                } else {
                    needDelete = true;
                    logger.warn("Document " + document.getId() + " has no file on disk");
                }

                if (needDelete) {
                    //TODO Delete file! Problem: write lock is active
                }
            }

            if (new File(fileUtil.getBASE_DIR()).isDirectory()) {
                Path start = Paths.get(fileUtil.getBASE_DIR());
                try (Stream<Path> stream = Files.walk(start, Integer.MAX_VALUE)) {
                    List<File> collect = stream
                            .map(Path::toFile)
                            .filter(File::isFile)
                            .filter(Predicate.not(checkedFiles::contains))
                            .collect(Collectors.toList());

                    collect.forEach(file -> {
                        logger.error(file.getName() + " has no Database Entry!");
                        if (file.delete()) {
                            logger.warn("Delete file " + file.getAbsolutePath());
                        }
                    });

                } catch (IOException e) {
                    logger.error("IO Exception on scan documents files on disk", e);
                }
            }

        } catch (InterruptedException | TimeoutException e) {
            logger.warn("Ran in timeout!", e);
        } finally {
            databaseLockService.unlockNewWriteOperations();
        }
    }

    // Rate = 6h
    // Initial Delay = 1 m
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000, initialDelay = 60 * 1000)
    public void deleteOldDocumentEntries() {
        logger.info("Search for Documents to delete");
        int deleteCounter = 0;

        Instant now = Instant.now();

        List<Date> dateList = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            dateList.add(Date.from(now.minus(i, ChronoUnit.DAYS)));
        }

        HashMap<String, UserDetails> userHashMap = new HashMap<>();

        List<Document> documentList = documentSearch.getDocumentsWithDeleteFilter(dateList);
        for (Document document : documentList) {
            UserDetails userDetails;
            if (userHashMap.containsKey(document.getUserId())) {
                userDetails = userHashMap.get(document.getUserId());
            } else {
                try {
                    userDetails = this.userDetailsService.loadUserById(document.getUserId());
                    userHashMap.put(document.getUserId(), userDetails);
                } catch (UsernameNotFoundException e) {
                    logger.error("Cannot delete document " + document.getId() + " because user " + document.getUserId() + " not exists!");
                    continue;
                }
            }

            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );
            documentController.deleteDocument(usernamePasswordAuthenticationToken, document.getId());
            deleteCounter++;
        }

        logger.info("Finished delete " + deleteCounter + " old documents");
    }
}

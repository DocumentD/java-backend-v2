package de.skillkiller.documentdbackend.task;

import de.skillkiller.documentdbackend.controller.DocumentController;
import de.skillkiller.documentdbackend.entity.Document;
import de.skillkiller.documentdbackend.search.MeiliSearch;
import de.skillkiller.documentdbackend.service.UserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

@Component
public class DeleteOldEntries {

    private static final Logger logger = LoggerFactory.getLogger(DeleteOldEntries.class);
    private final MeiliSearch meiliSearch;
    private final DocumentController documentController;
    private final UserDetailsService userDetailsService;

    public DeleteOldEntries(MeiliSearch meiliSearch, DocumentController documentController, UserDetailsService userDetailsService) {
        this.meiliSearch = meiliSearch;
        this.documentController = documentController;
        this.userDetailsService = userDetailsService;
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

        List<Document> documentList = meiliSearch.getDocumentsWithDeleteFilter(dateList);
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

package de.skillkiller.documentdbackend.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.skillkiller.documentdbackend.entity.http.meilisearch.request.CreateIndexRequest;
import de.skillkiller.documentdbackend.service.DatabaseLockService;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
public class MeiliSearch {

    private final String hostUrl;
    private final String privateApiKey;
    private final DatabaseLockService databaseLockService;
    private static final Logger logger = LoggerFactory.getLogger(MeiliSearch.class);

    public MeiliSearch(@Value("${meilisearch.hosturl}") String hostUrl,
                       @Value("${meilisearch.privateapikey}") String privateApiKey,
                       @Value("${meilisearch.indexprefix}") String indexPrefix, ObjectMapper objectMapper, DatabaseLockService databaseLockService) {
        this.hostUrl = hostUrl;
        this.privateApiKey = privateApiKey;
        this.databaseLockService = databaseLockService;
    }

    protected boolean hasAllUpdatesProcessed(String primaryKey) {
        HttpResponse<List> request = Unirest.get(hostUrl + "/indexes/{index_uid}/updates")
                .routeParam("index_uid", primaryKey)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(List.class);
        if (request.getStatus() == 200) {
            boolean processed = true;
            for (Object o : request.getBody()) {
                LinkedHashMap<String, Object> entry = (LinkedHashMap<String, Object>) o;
                String status = (String) entry.getOrDefault("status", "failed");
                if (status.equals("enqueued")) {
                    processed = false;
                    break;
                }
            }
            return processed;
        } else {
            return false;
        }
    }

    protected boolean createIndex(String uid, String primaryKey) throws TimeoutException, InterruptedException {
        databaseLockService.requestDoingWriteOperation();
        HttpResponse<JsonNode> request = Unirest.post(hostUrl + "/indexes")
                .body(new CreateIndexRequest(uid, primaryKey))
                .header("X-Meili-API-Key", privateApiKey)
                .asJson();

        databaseLockService.completeWriteOperation();
        return request.getStatus() == 201;
    }

    protected boolean createOrReplaceMeiliDocument(Object o, String primaryKey) throws TimeoutException, InterruptedException {
        databaseLockService.requestDoingWriteOperation();
        HttpResponse request = Unirest.post(hostUrl + "/indexes/{index_uid}/documents")
                .body(Collections.singletonList(o))
                .routeParam("index_uid", primaryKey)
                .header("X-Meili-API-Key", privateApiKey)
                .asEmpty();

        databaseLockService.completeWriteOperation();
        return request.getStatus() == 202;
    }

    protected void deleteMeiliDocument(String indexName, String id) throws TimeoutException, InterruptedException {
        databaseLockService.requestDoingWriteOperation();
        Unirest.delete(hostUrl + "/indexes/{index_uid}/documents/{document_id}")
                .routeParam("index_uid", indexName)
                .routeParam("document_id", id)
                .header("X-Meili-API-Key", privateApiKey)
                .asEmptyAsync();
        databaseLockService.completeWriteOperation();
    }

}

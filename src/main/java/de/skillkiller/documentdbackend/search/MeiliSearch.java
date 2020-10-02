package de.skillkiller.documentdbackend.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.skillkiller.documentdbackend.search.request.CreateIndexRequest;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class MeiliSearch {

    private final String hostUrl;
    private final String privateApiKey;
    private static final Logger logger = LoggerFactory.getLogger(MeiliSearch.class);

    public MeiliSearch(@Value("${meilisearch.hosturl}") String hostUrl,
                       @Value("${meilisearch.privateapikey}") String privateApiKey,
                       @Value("${meilisearch.indexprefix}") String indexPrefix, ObjectMapper objectMapper) {
        this.hostUrl = hostUrl;
        this.privateApiKey = privateApiKey;
    }

    protected boolean createIndex(String uid, String primaryKey) {
        HttpResponse<JsonNode> request = Unirest.post(hostUrl + "/indexes")
                .body(new CreateIndexRequest(uid, primaryKey))
                .header("X-Meili-API-Key", privateApiKey)
                .asJson();

        return request.getStatus() == 201;
    }

    protected boolean createOrReplaceMeiliDocument(Object o, String primaryKey) {
        HttpResponse request = Unirest.post(hostUrl + "/indexes/{index_uid}/documents")
                .body(Collections.singletonList(o))
                .routeParam("index_uid", primaryKey)
                .header("X-Meili-API-Key", privateApiKey)
                .asEmpty();

        return request.getStatus() == 202;
    }

    protected void deleteMeiliDocument(String indexName, String id) {
        Unirest.delete(hostUrl + "/indexes/{index_uid}/documents/{document_id}")
                .routeParam("index_uid", indexName)
                .routeParam("document_id", id)
                .header("X-Meili-API-Key", privateApiKey)
                .asEmptyAsync();
    }

}

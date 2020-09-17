package de.skillkiller.documentdbackend.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.skillkiller.documentdbackend.entity.Document;
import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.entity.http.CreateIndexRequest;
import de.skillkiller.documentdbackend.entity.http.SearchResponse;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MeliSearch {

    private final String hostUrl;
    private final String privateApiKey;
    private final String userIndexName;
    private final String documentIndexName;
    private final ObjectMapper objectMapper;

    public MeliSearch(@Value("${meilisearch.hosturl}") String hostUrl,
                      @Value("${meilisearch.privateapikey}") String privateApiKey,
                      @Value("${meilisearch.indexprefix}") String indexPrefix, ObjectMapper objectMapper) {
        this.hostUrl = hostUrl;
        this.privateApiKey = privateApiKey;
        this.userIndexName = indexPrefix + "users";
        this.documentIndexName = indexPrefix + "documents";
        this.objectMapper = objectMapper;
    }

    public List<String> getAllIndexes() {
        HttpResponse<JsonNode> request = Unirest.get(hostUrl + "/indexes")
                .header("X-Meili-API-Key", privateApiKey)
                .asJson();
        List<String> indexes = new ArrayList<>();

        int len = request.getBody().getArray().length();

        for (int i = 0; i < len; i++) {
            indexes.add(request.getBody().getArray().getJSONObject(i).getString("name"));
        }

        return indexes;
    }

    public SearchResponse searchForTopDocumentsInUserScope(String userid) {
        //TODO Evt. unsorted Files
        HttpResponse<SearchResponse> request = Unirest.post(hostUrl + "/indexes/{index_uid}/search")
                .body(String.format("{\"facetFilters\":[\"userid:%s\"],\"limit\":20,\"matches\":true}", userid))
                .routeParam("index_uid", documentIndexName)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(SearchResponse.class);
        return request.getBody();
    }

    public SearchResponse searchForDocumentInUserScope(String userid, String searchQuery) {
        HttpResponse<SearchResponse> request = Unirest.post(hostUrl + "/indexes/{index_uid}/search")
                .body(String.format("{\"q\":\"%s\",\"facetFilters\":[\"userid:%s\"],\"matches\":true}", searchQuery, userid))
                .routeParam("index_uid", documentIndexName)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(SearchResponse.class);
        return request.getBody();
    }

    public SearchResponse getDocumentsWithCompanyFilterInUserScope(String userid, String company) {
        HttpResponse<SearchResponse> request = Unirest.post(hostUrl + "/indexes/{index_uid}/search")
                .body(String.format("{\"filters\":\"company = \\\"%s\\\"\",\"facetFilters\":[\"userid:%s\"]}", company, userid))
                .routeParam("index_uid", documentIndexName)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(SearchResponse.class);
        return handleSearchResponseAndTransFormHitsToDocuments(request);
    }

    public SearchResponse getDocumentsWithCategoryFilterInUserScope(String userid, String company) {
        HttpResponse<SearchResponse> request = Unirest.post(hostUrl + "/indexes/{index_uid}/search")
                .body(String.format("{\"filters\":\"category = \\\"%s\\\"\",\"facetFilters\":[\"userid:%s\"]}", company, userid))
                .routeParam("index_uid", documentIndexName)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(SearchResponse.class);
        return handleSearchResponseAndTransFormHitsToDocuments(request);
    }

    public Optional<User> searchUserByUsername(String username) {
        HttpResponse<SearchResponse> request = Unirest.post(hostUrl + "/indexes/{index_uid}/search")
                .body(String.format("{\"filters\": \"username = '%S'\"}", username))
                .routeParam("index_uid", userIndexName)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(SearchResponse.class);

        if (request.getStatus() == 200) {
            SearchResponse response = request.getBody();
            if (response.getHits().size() == 1) {
                return Optional.of(objectMapper.convertValue(response.getHits().get(0), User.class));
            } else if (response.getHits().size() > 1) {
                throw new RuntimeException("Duplicated username " + username + "!\n Found " + response.getHits().size());
            }
        }
        return Optional.empty();
    }

    public Optional<User> getUserById(String userId) {
        HttpResponse<User> request = Unirest.get(hostUrl + "/indexes/{index_uid}/documents/{document_id}")
                .routeParam("index_uid", userIndexName)
                .routeParam("document_id", userId)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(User.class);

        if (request.getStatus() == 200) {
            return Optional.of(request.getBody());
        }
        return Optional.empty();
    }

    public Optional<Document> getDocumentById(String documentId) {
        HttpResponse<Document> request = Unirest.get(hostUrl + "/indexes/{index_uid}/documents/{document_id}")
                .routeParam("index_uid", documentIndexName)
                .routeParam("document_id", documentId)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(Document.class);

        if (request.getStatus() == 200) {
            return Optional.of(request.getBody());
        }
        return Optional.empty();
    }

    public Optional<Document> getDocumentByIdAndUserId(String documentId, String userId) {
        Optional<Document> optionalDocument = getDocumentById(documentId);
        if (optionalDocument.isPresent()) {
            if (optionalDocument.get().getUserId().equals(userId)) {
                return optionalDocument;
            }
        }
        return Optional.empty();
    }

    public boolean createOrReplaceUser(User user) {
        //TODO Check if username already exists
        return createOrReplaceJSONDocument(user, userIndexName);
    }

    public boolean createOrReplaceDocument(Document document) {
        return createOrReplaceJSONDocument(document, documentIndexName);
    }

    private boolean createOrReplaceJSONDocument(Object o, String primaryKey) {
        HttpResponse request = Unirest.post(hostUrl + "/indexes/{index_uid}/documents")
                .body(Collections.singletonList(o))
                .routeParam("index_uid", primaryKey)
                .header("X-Meili-API-Key", privateApiKey)
                .asEmpty();

        return request.getStatus() == 202;
    }

    public boolean createUserIndex() {
        boolean success = createIndex(userIndexName, "userid");

        if (success) {
            Unirest.post(hostUrl + "/indexes/{index_uid}/settings")
                    .body("{\"attributesForFaceting\":[\"username\",\"connectpasswordhash\",\"mailaddresses\"],\"searchableAttributes\":[]}")
                    .routeParam("index_uid", userIndexName)
                    .header("X-Meili-API-Key", privateApiKey)
                    .asEmptyAsync();
        }
        return success;
    }

    public boolean createDocumentIndex() {
        boolean success = createIndex(documentIndexName, "documentid");

        if (success) {
            Unirest.post(hostUrl + "/indexes/{index_uid}/settings")
                    .body("{\"attributesForFaceting\":[\"userid\"],\"searchableAttributes\":[\"documentid\",\"title\",\"documentdate\",\"deletedate\",\"pages\",\"textcontent\",\"pdftitle\",\"company\",\"category\",\"tags\"]}")
                    .routeParam("index_uid", documentIndexName)
                    .header("X-Meili-API-Key", privateApiKey)
                    .asEmptyAsync();
        }
        return success;
    }

    private boolean createIndex(String uid, String primaryKey) {
        HttpResponse<JsonNode> request = Unirest.post(hostUrl + "/indexes")
                .body(new CreateIndexRequest(uid, primaryKey))
                .header("X-Meili-API-Key", privateApiKey)
                .asJson();

        return request.getStatus() == 201;
    }

    public void deleteDocument(String documentId) {
        Unirest.delete(hostUrl + "/indexes/:index_uid/documents/:document_id")
                .routeParam("index_uid", documentIndexName)
                .routeParam("document_id", documentId)
                .header("X-Meili-API-Key", privateApiKey)
                .asEmptyAsync();
    }

    private SearchResponse handleSearchResponseAndTransFormHitsToDocuments(HttpResponse<SearchResponse> request) {
        SearchResponse searchResponse = request.getBody();
        List<Object> hits = searchResponse.getHits();
        List<Object> userHits = new LinkedList<>();
        for (Object hit : hits) {
            userHits.add(objectMapper.convertValue(hit, Document.class));
        }
        searchResponse.setHits(userHits);
        return request.getBody();
    }
}

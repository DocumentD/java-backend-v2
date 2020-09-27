package de.skillkiller.documentdbackend.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.internal.LinkedTreeMap;
import de.skillkiller.documentdbackend.entity.Document;
import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.entity.http.CreateIndexRequest;
import de.skillkiller.documentdbackend.entity.http.SearchResponse;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class MeiliSearch {

    private final String hostUrl;
    private final String privateApiKey;
    private final String userIndexName;
    private final String documentIndexName;
    private final ObjectMapper objectMapper;
    private final SimpleDateFormat DELETEDATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final Logger logger = LoggerFactory.getLogger(MeiliSearch.class);

    public MeiliSearch(@Value("${meilisearch.hosturl}") String hostUrl,
                       @Value("${meilisearch.privateapikey}") String privateApiKey,
                       @Value("${meilisearch.indexprefix}") String indexPrefix, ObjectMapper objectMapper) {
        this.hostUrl = hostUrl;
        this.privateApiKey = privateApiKey;
        this.userIndexName = indexPrefix + "users";
        this.documentIndexName = indexPrefix + "documents";
        this.objectMapper = objectMapper;
        this.DELETEDATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
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
        return searchForTopDocumentsInUserScope(userid, 0, 20);
    }

    public SearchResponse searchForTopDocumentsInUserScope(String userid, int offset, int limit) {
        HttpResponse<SearchResponse> request = Unirest.post(hostUrl + "/indexes/{index_uid}/search")
                .body(String.format("{\"facetFilters\":[\"userid:%s\",\"company:null\"],\"offset\":%s,\"limit\":%s,\"matches\":true}", userid, offset, limit))
                .routeParam("index_uid", documentIndexName)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(SearchResponse.class);
        return replaceDocumentsCompanyName(request);
    }

    private SearchResponse replaceDocumentsCompanyName(HttpResponse<SearchResponse> request) {
        if (request.getBody().getHits() != null) {
            for (Object hit : request.getBody().getHits()) {
                if (hit instanceof LinkedHashMap) {
                    LinkedHashMap<String, Object> linkedHashMap = (LinkedHashMap<String, Object>) hit;
                    Object obj = linkedHashMap.get("company");
                    if (obj instanceof String) {
                        if ("null".equals(obj)) linkedHashMap.put("company", null);
                    }
                }
            }
        }
        return request.getBody();
    }

    public SearchResponse searchForDocumentInUserScope(String userid, String searchQuery) {
        return searchForDocumentInUserScope(userid, searchQuery, 0, 20);
    }

    public SearchResponse searchForDocumentInUserScope(String userid, String searchQuery, int offset, int limit) {
        HttpResponse<SearchResponse> request = Unirest.post(hostUrl + "/indexes/{index_uid}/search")
                .body(String.format("{\"q\":\"%s\",\"facetFilters\":[\"userid:%s\"],\"offset\":%s,\"limit\":%s,\"matches\":true}", searchQuery, userid, offset, limit))
                .routeParam("index_uid", documentIndexName)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(SearchResponse.class);

        return replaceDocumentsCompanyName(request);
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

    public List<Document> getDocumentsWithDeleteFilter(List<Date> datesToGet) {
        List<Document> documentList = new ArrayList<>();
        int offset = 0;
        SearchResponse searchResponse;
        do {
            searchResponse = getDocumentsWithDeleteFilter(datesToGet, 1, offset);
            if (searchResponse.getHits() != null) {
                for (Object hit : searchResponse.getHits()) {
                    if (hit instanceof Document) {
                        documentList.add((Document) hit);
                    }
                }
            }

            offset += searchResponse.getLimit();
        } while (searchResponse.getNbHits() > (searchResponse.getOffset() + searchResponse.getLimit()));

        return documentList;
    }

    private SearchResponse getDocumentsWithDeleteFilter(List<Date> datesToGet, int limit, int offset) {
        if (datesToGet.size() == 0) throw new RuntimeException("Dates list is empty!");
        StringBuilder stringBuilder = new StringBuilder("[[");
        String pattern = "\"deletedate:%s\"";
        stringBuilder.append(String.format(pattern, DELETEDATE_FORMAT.format(datesToGet.get(0))));
        for (int i = 1; i < datesToGet.size(); i++) {
            stringBuilder.append(",");
            stringBuilder.append(String.format(pattern, DELETEDATE_FORMAT.format(datesToGet.get(i))));
        }
        stringBuilder.append("]]");

        HttpResponse<SearchResponse> request = Unirest.post(hostUrl + "/indexes/{index_uid}/search")
                .body(String.format("{\"facetFilters\": %s,\"limit\":%s,\"offset\":%s}", stringBuilder.toString(), limit, offset))
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
        HttpResponse<LinkedTreeMap> request = Unirest.get(hostUrl + "/indexes/{index_uid}/documents/{document_id}")
                .routeParam("index_uid", userIndexName)
                .routeParam("document_id", userId)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(LinkedTreeMap.class);

        if (request.getStatus() == 200) {
            return Optional.of(objectMapper.convertValue(request.getBody(), User.class));
        }
        return Optional.empty();
    }

    public Optional<User> getUserByMailAddress(String mailAddress) {
        HttpResponse<SearchResponse> request = Unirest.post(hostUrl + "/indexes/{index_uid}/search")
                .body(String.format("{\"facetFilters\": [\"mailaddresses:%s\"],\"offset\":0,\"limit\":1}", mailAddress))
                .routeParam("index_uid", userIndexName)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(SearchResponse.class);

        if (request.getStatus() == 200 && request.getBody().getNbHits() == 1) {
            return Optional.of(objectMapper.convertValue(request.getBody().getHits().get(0), User.class));
        } else if (request.getBody().getNbHits() > 1) {
            logger.error("Multiple user with " + mailAddress);
        }
        return Optional.empty();
    }

    public Optional<User> getUserByConnectPassword(String connectPassword) {
        HttpResponse<SearchResponse> request = Unirest.post(hostUrl + "/indexes/{index_uid}/search")
                .body(String.format("{\"facetFilters\": [\"connectpassword:%s\"],\"offset\":0,\"limit\":1}", connectPassword))
                .routeParam("index_uid", userIndexName)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(SearchResponse.class);

        if (request.getStatus() == 200 && request.getBody().getNbHits() == 1) {
            return Optional.of(objectMapper.convertValue(request.getBody().getHits().get(0), User.class));
        } else if (request.getBody().getNbHits() > 1) {
            logger.error("Multiple user with connect password" + connectPassword);
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

    public boolean hasSystemUsers() {
        HttpResponse<SearchResponse> request = Unirest.post(hostUrl + "/indexes/{index_uid}/search")
                .body("{\"limit\": 1}")
                .routeParam("index_uid", userIndexName)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(SearchResponse.class);

        return request.getBody().getHits() != null && request.getBody().getHits().size() >= 1;
    }

    public boolean createOrReplaceUser(User user) {
        //TODO Check if username already exists
        return createOrReplaceJSONDocument(user, userIndexName);
    }

    public boolean createOrReplaceDocument(Document document) {
        if (document.getCompany() == null) document.setCompany("null");
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
                    .body("{\"attributesForFaceting\":[\"username\",\"connectpassword\",\"mailaddresses\"],\"searchableAttributes\":[]}")
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
                    .body("{\"attributesForFaceting\": [\"company\",\"userid\",\"deletedate\"],\"searchableAttributes\":[\"documentid\",\"title\",\"documentdate\",\"deletedate\",\"tags\",\"pdftitle\",\"company\",\"category\",\"textcontent\",\"filename\"]}")
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
        Unirest.delete(hostUrl + "/indexes/{index_uid}/documents/{document_id}")
                .routeParam("index_uid", documentIndexName)
                .routeParam("document_id", documentId)
                .header("X-Meili-API-Key", privateApiKey)
                .asEmptyAsync();
    }

    private SearchResponse handleSearchResponseAndTransFormHitsToDocuments(HttpResponse<SearchResponse> request) {
        SearchResponse searchResponse = request.getBody();
        List<Object> hits = searchResponse.getHits();
        List<Object> userHits = new LinkedList<>();
        if (hits != null) {
            for (Object hit : hits) {
                Document document = objectMapper.convertValue(hit, Document.class);
                if (document.getCompany().equals("null")) document.setCompany(null);
                userHits.add(document);
            }
            searchResponse.setHits(userHits);
        }
        return request.getBody();
    }
}

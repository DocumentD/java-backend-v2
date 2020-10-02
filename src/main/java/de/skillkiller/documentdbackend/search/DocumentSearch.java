package de.skillkiller.documentdbackend.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.skillkiller.documentdbackend.entity.Document;
import de.skillkiller.documentdbackend.search.response.SearchResponse;
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
public class DocumentSearch {

    private static final Logger logger = LoggerFactory.getLogger(DocumentSearch.class);
    private final String hostUrl;
    private final String privateApiKey;
    private final String documentIndexName;
    private final ObjectMapper objectMapper;
    private final SimpleDateFormat DELETEDATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private final MeiliSearch meiliSearch;

    public DocumentSearch(@Value("${meilisearch.hosturl}") String hostUrl,
                          @Value("${meilisearch.privateapikey}") String privateApiKey,
                          @Value("${meilisearch.indexprefix}") String indexPrefix, ObjectMapper objectMapper, MeiliSearch meiliSearch) {
        this.hostUrl = hostUrl;
        this.privateApiKey = privateApiKey;
        this.documentIndexName = indexPrefix + "documents";
        this.objectMapper = objectMapper;
        this.meiliSearch = meiliSearch;
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

    public boolean createDocumentIndex() {
        boolean success = meiliSearch.createIndex(documentIndexName, "documentid");

        if (success) {
            Unirest.post(hostUrl + "/indexes/{index_uid}/settings")
                    .body("{\"attributesForFaceting\": [\"company\",\"userid\",\"deletedate\"],\"searchableAttributes\":[\"documentid\",\"title\",\"documentdate\",\"deletedate\",\"tags\",\"pdftitle\",\"company\",\"category\",\"textcontent\",\"filename\"]}")
                    .routeParam("index_uid", documentIndexName)
                    .header("X-Meili-API-Key", privateApiKey)
                    .asEmptyAsync();
        }
        return success;
    }

    public boolean createOrReplaceDocument(Document document) {
        if (document.getCompany() == null) document.setCompany("null");
        return meiliSearch.createOrReplaceMeiliDocument(document, documentIndexName);
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

    public void deleteDocument(String documentId) {
        meiliSearch.deleteMeiliDocument(documentIndexName, documentId);
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

package de.skillkiller.documentdbackend.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.internal.LinkedTreeMap;
import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.entity.http.SearchResponse;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class UserSearch {

    private static final Logger logger = LoggerFactory.getLogger(UserSearch.class);
    private final String hostUrl;
    private final String privateApiKey;
    private final String userIndexName;
    private final ObjectMapper objectMapper;
    private final MeiliSearch meiliSearch;

    public UserSearch(@Value("${meilisearch.hosturl}") String hostUrl,
                      @Value("${meilisearch.privateapikey}") String privateApiKey,
                      @Value("${meilisearch.indexprefix}") String indexPrefix, ObjectMapper objectMapper, MeiliSearch meiliSearch) {
        this.hostUrl = hostUrl;
        this.privateApiKey = privateApiKey;
        this.userIndexName = indexPrefix + "users";
        this.objectMapper = objectMapper;
        this.meiliSearch = meiliSearch;
    }

    public boolean createOrReplaceUser(User user) {
        //TODO Check if username already exists
        return meiliSearch.createOrReplaceMeiliDocument(user, userIndexName);
    }

    public boolean createUserIndex() {
        boolean success = meiliSearch.createIndex(userIndexName, "userid");

        if (success) {
            Unirest.post(hostUrl + "/indexes/{index_uid}/settings")
                    .body("{\"attributesForFaceting\":[\"username\",\"connectpassword\",\"mailaddresses\"],\"searchableAttributes\":[]}")
                    .routeParam("index_uid", userIndexName)
                    .header("X-Meili-API-Key", privateApiKey)
                    .asEmptyAsync();
        }
        return success;
    }

    public List<User> getUsers(int offset, int limit) {
        HttpResponse<List> request = Unirest.get(hostUrl + "/indexes/{index_uid}/documents")
                .queryString("offset", offset)
                .queryString("limit", limit)
                .queryString("attributesToRetrieve", "userid,username,administrator")
                .routeParam("index_uid", userIndexName)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(List.class);

        List<User> users = new ArrayList<>(request.getBody().size());
        for (Object o : request.getBody()) {
            users.add(objectMapper.convertValue(o, User.class));
        }

        return users;
    }

    public Optional<User> getUserByUsername(String username) {
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

    public boolean hasSystemUsers() {
        HttpResponse<SearchResponse> request = Unirest.post(hostUrl + "/indexes/{index_uid}/search")
                .body("{\"limit\": 1}")
                .routeParam("index_uid", userIndexName)
                .header("X-Meili-API-Key", privateApiKey)
                .asObject(SearchResponse.class);

        return request.getBody().getHits() != null && request.getBody().getHits().size() >= 1;
    }

    public void deleteUser(String userId) {
        meiliSearch.deleteMeiliDocument(userIndexName, userId);
    }
}

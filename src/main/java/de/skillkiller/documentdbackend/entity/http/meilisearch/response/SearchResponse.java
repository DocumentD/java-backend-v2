package de.skillkiller.documentdbackend.entity.http.meilisearch.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class SearchResponse {
    @JsonProperty
    private List<Object> hits;

    @JsonProperty
    private Integer offset;

    @JsonProperty
    private Integer limit;

    @JsonProperty
    private Integer nbHits;

    @JsonProperty
    private boolean exhaustiveNbHits;

    @JsonProperty
    private Integer processingTimeMs;

    @JsonProperty
    private String query;
}

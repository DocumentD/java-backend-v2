package de.skillkiller.documentdbackend.entity.http.meilisearch.response;

import lombok.Data;

@Data
public class StatsResponse {
    private Integer numberOfDocuments;
    private boolean isIndexing;
}

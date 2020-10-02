package de.skillkiller.documentdbackend.entity.http.meilisearch.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateIndexRequest {
    @JsonProperty
    private String uid;

    @JsonProperty
    private String primaryKey;
}

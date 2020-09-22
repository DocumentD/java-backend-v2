package de.skillkiller.documentdbackend.entity.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.skillkiller.documentdbackend.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDocumentResponse {
    @JsonProperty("document")
    private Document document;

    @JsonProperty("companies")
    private Set<String> companies = new HashSet<>();

    @JsonProperty("categories")
    private Set<String> categories = new HashSet<>();
}

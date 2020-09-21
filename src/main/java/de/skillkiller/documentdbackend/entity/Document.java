package de.skillkiller.documentdbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Data
public class Document {

    @JsonProperty("documentid")
    private String id;

    @JsonProperty
    private String title;

    @JsonProperty("documentdate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Europe/Berlin")
    private Date documentDate;

    @JsonProperty("deletedate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Europe/Berlin")
    private Date deleteDate;

    @JsonProperty
    private String filename;

    @JsonProperty
    private Integer pages;

    @JsonProperty("textcontent")
    private String textContent;

    @JsonProperty("pdftitle")
    private String pdfTitle;

    @JsonProperty("userid")
    private String userId;

    @JsonProperty
    private String company;

    @JsonProperty
    private String category;

    @JsonProperty
    private Set<String> tags = new HashSet<>();

}

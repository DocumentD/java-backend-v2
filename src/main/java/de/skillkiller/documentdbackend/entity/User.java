package de.skillkiller.documentdbackend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Data
public class User {
    @JsonProperty("userid")
    private String id;

    @JsonProperty
    private String username;

    @JsonProperty("modifydate")
    private Date modifyDate;

    @JsonProperty("passwordhash")
    private String passwordHash;

    @JsonProperty("connectpasswordhash")
    private String connectPasswordHash;

    @JsonProperty("mailaddresses")
    private Set<String> mailAddresses = new HashSet<>();

    @JsonProperty("companies")
    private Set<String> companies = new HashSet<>();

    @JsonProperty("categories")
    private Set<String> categories = new HashSet<>();
}

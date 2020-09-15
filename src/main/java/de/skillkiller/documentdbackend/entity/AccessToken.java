package de.skillkiller.documentdbackend.entity;

import lombok.Data;

import java.util.Date;

@Data
public class AccessToken {
    private String token;
    private String documentId;
    private Date expire;
}

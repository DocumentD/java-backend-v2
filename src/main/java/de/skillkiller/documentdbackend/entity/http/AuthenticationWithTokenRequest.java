package de.skillkiller.documentdbackend.entity.http;

import lombok.Data;

@Data
public class AuthenticationWithTokenRequest {
    private String token;
}

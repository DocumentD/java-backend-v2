package de.skillkiller.documentdbackend.entity.frontend.http.request;

import lombok.Data;

@Data
public class AuthenticationWithTokenRequest {
    private String token;
}

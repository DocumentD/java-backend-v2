package de.skillkiller.documentdbackend.entity.http.frontend.request;

import lombok.Data;

@Data
public class AuthenticationWithTokenRequest {
    private String token;
}

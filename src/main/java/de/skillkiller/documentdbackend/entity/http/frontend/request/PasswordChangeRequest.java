package de.skillkiller.documentdbackend.entity.http.frontend.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PasswordChangeRequest {
    @JsonProperty("oldpassword")
    private String oldPassword;

    @JsonProperty("newpassword")
    private String newPassword;
}

package de.skillkiller.documentdbackend.entity.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PasswordChangeRequest {
    @JsonProperty("oldpassword")
    private String oldPassword;

    @JsonProperty("newpassword")
    private String newPassword;
}

package de.skillkiller.documentdbackend.entity;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@Getter
public class UserDetailsHolder extends org.springframework.security.core.userdetails.User {

    private final User authenticatedUser;

    public UserDetailsHolder(User user, Collection<? extends GrantedAuthority> authorities) {
        super(user.getUsername(), user.getPasswordHash(), authorities);
        this.authenticatedUser = user;
    }
}

package de.skillkiller.documentdbackend.service;

import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.entity.UserDetailsHolder;
import de.skillkiller.documentdbackend.search.MeiliSearch;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
public class UserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {

    private final MeiliSearch meiliSearch;

    public UserDetailsService(MeiliSearch meiliSearch) {
        this.meiliSearch = meiliSearch;
    }

    @Override
    public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {
        Optional<User> optionalUser = meiliSearch.searchUserByUsername(s);

        if (optionalUser.isPresent()) {
            return new UserDetailsHolder(optionalUser.get(), Collections.emptyList());
        } else throw new UsernameNotFoundException("User not found");
    }

    public UserDetails loadUserById(String userId) throws UsernameNotFoundException {
        Optional<User> optionalUser = meiliSearch.getUserById(userId);

        if (optionalUser.isPresent()) {
            return new UserDetailsHolder(optionalUser.get(), Collections.emptyList());
        } else throw new UsernameNotFoundException("User not found");
    }
}

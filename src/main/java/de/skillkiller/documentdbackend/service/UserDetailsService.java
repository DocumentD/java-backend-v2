package de.skillkiller.documentdbackend.service;

import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.entity.UserDetailsHolder;
import de.skillkiller.documentdbackend.search.MeliSearch;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
public class UserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {

    private final MeliSearch meliSearch;

    public UserDetailsService(MeliSearch meliSearch) {
        this.meliSearch = meliSearch;
    }

    @Override
    public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {
        Optional<User> optionalUser = meliSearch.searchUserByUsername(s);

        if (optionalUser.isPresent()) {
            return new UserDetailsHolder(optionalUser.get(), Collections.emptyList());
        } else throw new UsernameNotFoundException("User not found");
    }

    public UserDetails loadUserById(String userId) throws UsernameNotFoundException {
        Optional<User> optionalUser = meliSearch.getUserById(userId);

        if (optionalUser.isPresent()) {
            return new UserDetailsHolder(optionalUser.get(), Collections.emptyList());
        } else throw new UsernameNotFoundException("User not found");
    }
}

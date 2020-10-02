package de.skillkiller.documentdbackend.controller;

import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.entity.UserDetailsHolder;
import de.skillkiller.documentdbackend.entity.http.PasswordChangeRequest;
import de.skillkiller.documentdbackend.search.UserSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("user")
@CrossOrigin(methods = {RequestMethod.PUT, RequestMethod.GET, RequestMethod.DELETE}, origins = {"*"})
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserSearch userSearch;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserSearch userSearch, PasswordEncoder passwordEncoder) {
        this.userSearch = userSearch;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("list")
    public ResponseEntity<List<User>> getUserList(Authentication authentication, @RequestParam int pageNumber, @RequestParam int pageSize) {
        User authenticatedUser = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();

        if (authenticatedUser.isAdministrator()) {
            return ResponseEntity.ok(userSearch.getUsers(pageNumber * pageSize, pageSize));
        }

        return ResponseEntity.status(403).build();
    }

    @GetMapping("connectpassword")
    public ResponseEntity<String> getNewMailConnectPassword(Authentication authentication) {
        User authenticatedUser = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();
        String connectPassword;
        int i = 0;
        do {
            connectPassword = "D:" + UUID.randomUUID().toString();
            i++;
        } while (userSearch.getUserByConnectPassword(connectPassword).isPresent() && i < 10);

        if (i == 10) {
            logger.error("No free connect password with 10 try's found!");
            return ResponseEntity.status(500).build();
        }

        authenticatedUser.setConnectPassword(connectPassword);
        userSearch.createOrReplaceUser(authenticatedUser);
        return ResponseEntity.ok(connectPassword);
    }

    @PutMapping("username")
    public ResponseEntity<Void> setUsername(Authentication authentication, @RequestParam String username) {
        User authenticatedUser = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();

        if (userSearch.getUserByUsername(username).isEmpty()) {
            authenticatedUser.setUsername(username);
            userSearch.createOrReplaceUser(authenticatedUser);
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.badRequest().build();
    }

    @PutMapping("password")
    public ResponseEntity<Void> changePassword(Authentication authentication, @RequestBody PasswordChangeRequest passwordChangeRequest) {
        User authenticatedUser = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();

        if (passwordChangeRequest.getOldPassword() != null && passwordChangeRequest.getNewPassword() != null &&
                !passwordChangeRequest.getNewPassword().isBlank() && passwordChangeRequest.getNewPassword().length() > 4) {
            if (passwordEncoder.matches(passwordChangeRequest.getOldPassword(), authenticatedUser.getPasswordHash())) {
                authenticatedUser.setPasswordHash(passwordEncoder.encode(passwordChangeRequest.getNewPassword()));
                authenticatedUser.setModifyDate(new Date());
                userSearch.createOrReplaceUser(authenticatedUser);
                return ResponseEntity.ok().build();
            }
        }

        return ResponseEntity.badRequest().build();
    }

    @DeleteMapping("/mail/{mail}")
    public ResponseEntity<Void> deleteConnectedMailAddress(Authentication authentication, @PathVariable("mail") String mailAddress) {
        User authenticatedUser = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();
        Set<String> mailAddresses = authenticatedUser.getMailAddresses();

        if (mailAddresses != null && mailAddresses.contains(mailAddress)) {
            mailAddresses.remove(mailAddress);
            userSearch.createOrReplaceUser(authenticatedUser);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }
}

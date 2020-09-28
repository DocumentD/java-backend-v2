package de.skillkiller.documentdbackend.controller;

import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.entity.UserDetailsHolder;
import de.skillkiller.documentdbackend.entity.http.PasswordChangeRequest;
import de.skillkiller.documentdbackend.search.MeiliSearch;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("user")
@CrossOrigin(methods = {RequestMethod.POST, RequestMethod.GET, RequestMethod.DELETE}, origins = {"*"})
public class UserController {

    private final MeiliSearch meiliSearch;
    private final PasswordEncoder passwordEncoder;

    public UserController(MeiliSearch meiliSearch, PasswordEncoder passwordEncoder) {
        this.meiliSearch = meiliSearch;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("list")
    public ResponseEntity<List<User>> getUserList(Authentication authentication, @RequestParam int pageNumber, @RequestParam int pageSize) {
        User authenticatedUser = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();

        if (authenticatedUser.isAdministrator()) {
            return ResponseEntity.ok(meiliSearch.getUsers(pageNumber * pageSize, pageSize));
        }

        return ResponseEntity.status(403).build();
    }

    @PostMapping("/changepassword")
    public ResponseEntity<Void> changePassword(Authentication authentication, @RequestBody PasswordChangeRequest passwordChangeRequest) {
        User authenticatedUser = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();

        if (passwordChangeRequest.getOldPassword() != null && passwordChangeRequest.getNewPassword() != null &&
                !passwordChangeRequest.getNewPassword().isBlank() && passwordChangeRequest.getNewPassword().length() > 4) {
            if (passwordEncoder.matches(passwordChangeRequest.getOldPassword(), authenticatedUser.getPasswordHash())) {
                authenticatedUser.setPasswordHash(passwordEncoder.encode(passwordChangeRequest.getNewPassword()));
                authenticatedUser.setModifyDate(new Date());
                meiliSearch.createOrReplaceUser(authenticatedUser);
                return ResponseEntity.ok().build();
            }
        }

        return ResponseEntity.status(403).build();
    }
}

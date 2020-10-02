package de.skillkiller.documentdbackend.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.entity.UserDetailsHolder;
import de.skillkiller.documentdbackend.entity.http.AuthenticationRequest;
import de.skillkiller.documentdbackend.entity.http.AuthenticationWithTokenRequest;
import de.skillkiller.documentdbackend.search.UserSearch;
import de.skillkiller.documentdbackend.util.JWTUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Optional;

@RestController
@CrossOrigin(exposedHeaders = {"token"}, methods = {RequestMethod.POST, RequestMethod.GET}, origins = {"*"})
public class AuthorizationController {
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationController.class);

    private final JWTUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserSearch userSearch;

    public AuthorizationController(JWTUtil jwtUtil, AuthenticationManager authenticationManager, UserSearch userSearch) {
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.userSearch = userSearch;
    }

    @PostMapping(value = "/login")
    public ResponseEntity<User> login(@RequestBody AuthenticationRequest authenticationRequest) {
        logger.debug("Received Login Request");
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authenticationRequest.getUsername(), authenticationRequest.getPassword())
            );
            User user = ((UserDetailsHolder) authentication.getPrincipal()).getAuthenticatedUser();
            return ResponseEntity.ok().header("Token", jwtUtil.generateToken(user))
                    .body(user);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping(value = "/loginwithtoken")
    public ResponseEntity<User> loginWithToken(@RequestBody AuthenticationWithTokenRequest authenticationWithTokenRequest) {
        logger.debug("Received Login Request");
        DecodedJWT decodedJWT = jwtUtil.validateToken(authenticationWithTokenRequest.getToken());
        if (decodedJWT != null) {
            String userid = decodedJWT.getSubject();
            if (userid != null) {
                Optional<User> optionalUser = userSearch.getUserById(userid);

                if (optionalUser.isPresent()) {
                    User user = optionalUser.get();
                    if (user.getModifyDate().before(decodedJWT.getIssuedAt()) && decodedJWT.getExpiresAt().after(new Date())) {
                        return ResponseEntity.ok(user);
                    }
                }
            }
        }

        return ResponseEntity.status(401).build();
    }
}

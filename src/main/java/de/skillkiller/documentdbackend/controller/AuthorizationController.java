package de.skillkiller.documentdbackend.controller;

import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.entity.UserDetailsHolder;
import de.skillkiller.documentdbackend.entity.http.AuthenticationRequest;
import de.skillkiller.documentdbackend.search.MeliSearch;
import de.skillkiller.documentdbackend.util.JWTUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(exposedHeaders = {"token"}, methods = {RequestMethod.POST, RequestMethod.GET}, origins = {"*"})
public class AuthorizationController {
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationController.class);

    private final MeliSearch meliSearch;
    private final JWTUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthorizationController(MeliSearch meliSearch, JWTUtil jwtUtil, AuthenticationManager authenticationManager) {
        this.meliSearch = meliSearch;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
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
}

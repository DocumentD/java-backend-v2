package de.skillkiller.documentdbackend.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import de.skillkiller.documentdbackend.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class JWTUtil {
    private final Algorithm ALGORITHM;

    public JWTUtil(@Value("${jwt.secret}") final String jwtSecret) {
        System.out.println(jwtSecret);
        this.ALGORITHM = Algorithm.HMAC512(jwtSecret);
    }

    public String generateToken(User user) {
        return JWT.create()
                .withExpiresAt(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24))
                .withSubject(user.getId())
                .withIssuedAt(new Date())
                .withClaim("id", user.getId())
                .sign(ALGORITHM);
    }

    public DecodedJWT validateToken(String token) {
        try {
            JWTVerifier verifier = JWT.require(ALGORITHM)
                    .build();
            return verifier.verify(token);
        } catch (JWTVerificationException | IllegalArgumentException exception) {
            return null;
        }
    }
}

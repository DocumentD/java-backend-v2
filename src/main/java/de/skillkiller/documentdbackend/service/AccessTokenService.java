package de.skillkiller.documentdbackend.service;

import de.skillkiller.documentdbackend.entity.AccessToken;
import de.skillkiller.documentdbackend.util.RandomString;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccessTokenService {
    private final ConcurrentHashMap<String, AccessToken> accessTokenConcurrentHashMap = new ConcurrentHashMap<>();
    private final RandomString randomString = new RandomString(10);

    //TODO Scan hashmap periodically
    public Optional<AccessToken> isValidAndGet(String accessToken) {
        if (accessTokenConcurrentHashMap.containsKey(accessToken)) {
            AccessToken accessToken1 = accessTokenConcurrentHashMap.get(accessToken);
            if (accessToken1.getExpire().after(new Date())) {
                return Optional.of(accessToken1);
            } else {
                accessTokenConcurrentHashMap.remove(accessToken);
            }
        }
        return Optional.empty();
    }

    public AccessToken putToken(AccessToken accessToken) {
        do {
            accessToken.setToken(randomString.nextString());
        } while (accessTokenConcurrentHashMap.containsKey(accessToken.getToken()));
        accessTokenConcurrentHashMap.put(accessToken.getToken(), accessToken);
        return accessToken;
    }
}

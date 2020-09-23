package de.skillkiller.documentdbackend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.search.MeiliSearch;
import kong.unirest.Unirest;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class DatabaseInitializer {

    private final MeiliSearch meiliSearch;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final String firstUserUsername;
    private final String firstUserPassword;

    public DatabaseInitializer(MeiliSearch meiliSearch, ObjectMapper objectMapper, PasswordEncoder passwordEncoder,
                               @Value("${firstuser.username:admin}") String firstUserUsername,
                               @Value("${firstuser.password:${random.value}}") String firstUserPassword) {
        this.meiliSearch = meiliSearch;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.firstUserUsername = firstUserUsername;
        this.firstUserPassword = firstUserPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void doSomethingAfterStartup() {
        Unirest.config().setObjectMapper(new kong.unirest.ObjectMapper() {

            @Override
            public String writeValue(Object value) {
                try {
                    return objectMapper.writeValueAsString(value);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public <T> T readValue(String value, Class<T> valueType) {
                try {
                    return objectMapper.readValue(value, valueType);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        logger.trace("Create user index if not exists: " + meiliSearch.createUserIndex());
        logger.trace("Create document index if not exists: " + meiliSearch.createDocumentIndex());

        if (!meiliSearch.hasSystemUsers()) {
            User user = new User();

            user.setUsername(firstUserUsername);
            user.setId(DigestUtils.sha1Hex(System.currentTimeMillis() + "#" + user.getUsername()));
            user.setModifyDate(new Date());
            user.setPasswordHash(passwordEncoder.encode(firstUserPassword));
            user.setAdministrator(true);

            if (meiliSearch.createOrReplaceUser(user)) {
                logger.info("Create first user for system:");
                logger.info("Username: " + user.getUsername());
                logger.info("Password: " + firstUserPassword);
            } else {
                logger.error("Creating first user failed!");
            }

        }
    }
}

package de.skillkiller.documentdbackend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.skillkiller.documentdbackend.entity.Document;
import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.search.MeliSearch;
import kong.unirest.Unirest;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class ReadyListener {

    private final MeliSearch meliSearch;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    public ReadyListener(MeliSearch meliSearch, ObjectMapper objectMapper, PasswordEncoder passwordEncoder) {
        this.meliSearch = meliSearch;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
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
        System.out.println(meliSearch.getAllIndexes());
        System.out.println("meliSearch.createUserIndex() = " + meliSearch.createUserIndex());
        System.out.println("meliSearch.createDocumentIndex() = " + meliSearch.createDocumentIndex());
        User user = new User();

        user.setUsername("Skillkiller");
        user.setId(DigestUtils.sha1Hex(System.currentTimeMillis() + "#" + user.getUsername()));
        user.setModifyDate(new Date());
        user.setPasswordHash(passwordEncoder.encode("HASH"));
        System.out.println("user = " + user);

        Document document = new Document();

        document.setFilename("test.pdf");
        document.setPages(1);
        document.setTitle("Title");
        document.setUserId("c714c2416ff5a0fc18a131aaf38e93a56a0f64a6");
        document.setDocumentDate(new Date());
        document.setId("doc3");

        System.out.println("meliSearch.createOrReplaceDocument(document) = " + meliSearch.createOrReplaceDocument(document));

        //System.out.println("meliSearch.createOrReplaceUser(user) = " + meliSearch.createOrReplaceUser(user));

        System.out.println("meliSearch.searchUserByUsername(\"Skillkiller\") = " + meliSearch.searchUserByUsername("Skillkiller"));
    }
}

package de.skillkiller.documentdbackend.eventlistener;


import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.event.ConnectMailReceivedEvent;
import de.skillkiller.documentdbackend.search.MeiliSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.mail.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;

@Component
public class ConnectMailListener implements ApplicationListener<ConnectMailReceivedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(ConnectMailListener.class);

    private final MeiliSearch meiliSearch;

    public ConnectMailListener(MeiliSearch meiliSearch) {
        this.meiliSearch = meiliSearch;
    }

    @Override
    public void onApplicationEvent(ConnectMailReceivedEvent connectMailReceivedEvent) {
        logger.debug("Connect Mail Received!");
        Message message = connectMailReceivedEvent.getMessage();
        try {
            String content = getStringContent(message.getContent()).trim();
            if (!content.isBlank() && content.startsWith("D:")) {
                Optional<User> optionalUser = meiliSearch.getUserByConnectPassword(content);
                if (optionalUser.isPresent()) {
                    User user = optionalUser.get();
                    Address[] fromAddresses = message.getFrom();
                    if (fromAddresses.length > 0) {
                        Address fromAddress = fromAddresses[0];
                        Optional<User> optionalMailAddress = meiliSearch.getUserByMailAddress(fromAddress.toString());

                        // Only connect when mail address is not already linked with a account
                        if (optionalMailAddress.isEmpty()) {
                            if (user.getMailAddresses() == null) user.setMailAddresses(new HashSet<>());
                            user.getMailAddresses().add(fromAddress.toString());
                            meiliSearch.createOrReplaceUser(user);
                            logger.info("Connect new mail address with user " + user.getId());
                        }

                    }
                }
            }
        } catch (IOException | MessagingException e) {
            logger.error("Cannot handle connect mail", e);
        }

        try {
            message.setFlag(Flags.Flag.DELETED, true);
            message.getFolder().expunge();
        } catch (MessagingException e) {
            logger.error("Cannot delete connect mail", e);
        }

    }

    private String getStringContent(Object content) {
        if (content instanceof String)
            return (String) content;

        if (content instanceof Multipart) {
            try {
                int parts = ((Multipart) content).getCount();
                for (int i = 0; i < parts; i++) {
                    BodyPart bodyPart = ((Multipart) content).getBodyPart(i);
                    if (bodyPart.getContentType().startsWith("text/plain;")) {
                        return (String) bodyPart.getContent();
                    }
                }
            } catch (MessagingException | IOException e) {
                e.printStackTrace();
            }
        }

        return "";
    }
}

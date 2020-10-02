package de.skillkiller.documentdbackend.eventlistener;


import de.skillkiller.documentdbackend.controller.DocumentController;
import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.event.DocumentMailReceivedEvent;
import de.skillkiller.documentdbackend.search.UserSearch;
import de.skillkiller.documentdbackend.service.UserDetailsService;
import de.skillkiller.documentdbackend.util.SimpleMultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class DocumentMailListener implements ApplicationListener<DocumentMailReceivedEvent> {
    private static final Logger logger = LoggerFactory.getLogger(DocumentMailListener.class);
    private final UserSearch meiliSearch;
    private final DocumentController documentController;
    private final UserDetailsService userDetailsService;

    public DocumentMailListener(UserSearch meiliSearch, DocumentController documentController, UserDetailsService userDetailsService) {
        this.meiliSearch = meiliSearch;
        this.documentController = documentController;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public void onApplicationEvent(DocumentMailReceivedEvent documentMailReceivedEvent) {
        logger.debug("Received document mail");
        try {
            Message message = documentMailReceivedEvent.getMessage();
            message.getAllHeaders().asIterator().forEachRemaining(header -> System.out.println(header.getName() + ": " + header.getValue()));
            Address[] fromAddresses = message.getFrom();
            if (fromAddresses.length > 0) {
                Address fromAddress = fromAddresses[0];
                if (fromAddress instanceof InternetAddress) {
                    InternetAddress internetAddress = (InternetAddress) fromAddress;
                    Optional<User> optionalUser = meiliSearch.getUserByMailAddress(internetAddress.getAddress());

                    if (optionalUser.isPresent()) {
                        User user = optionalUser.get();
                        logger.debug("Sender has connected account");

                        UserDetails userDetails;
                        try {
                            userDetails = this.userDetailsService.loadUserById(user.getId());
                        } catch (UsernameNotFoundException e) {
                            logger.error("Cannot cannot upload document for user " + user.getId());
                            return;
                        }


                        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );

                        List<BodyPart> bodyParts = getAttachment(message.getContent());

                        for (BodyPart bodyPart : bodyParts) {
                            InputStream inputStream = bodyPart.getInputStream();

                            ByteBuffer byteBuffer = ByteBuffer.allocate(bodyPart.getSize() * 10);
                            byteBuffer.mark();

                            byte[] buf = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buf)) != -1) {
                                byteBuffer.put(buf, 0, bytesRead);
                            }

                            byteBuffer.flip();

                            byte[] bytes = new byte[byteBuffer.remaining()];
                            byteBuffer.get(bytes);

                            SimpleMultipartFile base64DecodedMultipartFile = new SimpleMultipartFile(bytes, bodyPart.getFileName());

                            documentController.handleFileUpload(usernamePasswordAuthenticationToken, base64DecodedMultipartFile);
                        }
                    } else {
                        logger.debug("Sender " + internetAddress.toString() + " has no connected account");
                    }

                } else {
                    logger.error("Mail Sender is not a internet address: " + fromAddress);
                }
            } else {
                logger.error("Mail has no from address");
            }

            message.setFlag(Flags.Flag.DELETED, true);
            message.getFolder().expunge();
        } catch (MessagingException | IOException e) {
            e.printStackTrace();
        }
    }

    private List<BodyPart> getAttachment(Object content) throws MessagingException, IOException {

        if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            List<BodyPart> result = new ArrayList<>();

            for (int i = 0; i < multipart.getCount(); i++) {
                result.addAll(getAttachment(multipart.getBodyPart(i)));
            }
            return result;

        }
        return Collections.emptyList();
    }

    private List<BodyPart> getAttachment(BodyPart part) throws IOException, MessagingException {
        List<BodyPart> result = new ArrayList<>();
        Object content = part.getContent();

        if (content instanceof InputStream) {
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                result.add(part);
                return result;
            } else {
                return new ArrayList<>();
            }
        }

        if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                result.addAll(getAttachment(bodyPart));
            }
        }
        return result;
    }

}

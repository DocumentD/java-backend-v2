package de.skillkiller.documentdbackend.service;

import de.skillkiller.documentdbackend.event.ConnectMailReceivedEvent;
import de.skillkiller.documentdbackend.event.DocumentMailReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.Arrays;
import java.util.List;

@Service
public class MailToEventProcessorService {
    private static final Logger logger = LoggerFactory.getLogger(MailToEventProcessorService.class);
    private static final List<String> connectMailNames = Arrays.asList("connect", "verbinden");

    private final ApplicationEventPublisher applicationEventPublisher;

    public MailToEventProcessorService(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void proceedMail(Message message) {
        try {
            Address[] recipientAddresses = message.getAllRecipients();
            for (Address recipientAddress : recipientAddresses) {
                String recipient = recipientAddress.toString();
                String[] part = recipient.split("@");
                if (connectMailNames.contains(part[0])) {
                    ConnectMailReceivedEvent connectMailReceivedEvent = new ConnectMailReceivedEvent(this, message);
                    applicationEventPublisher.publishEvent(connectMailReceivedEvent);
                } else {
                    DocumentMailReceivedEvent documentMailReceivedEvent = new DocumentMailReceivedEvent(this, message);
                    applicationEventPublisher.publishEvent(documentMailReceivedEvent);
                }
                return;
            }

            MailToEventProcessorService.logger.warn("Received mail with no recipient!");
        } catch (MessagingException e) {
            MailToEventProcessorService.logger.error("Error by sorting mail into event", e);
        }
    }

}

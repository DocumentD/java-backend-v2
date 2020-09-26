package de.skillkiller.documentdbackend.service;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Scope("singleton")
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);
    private final String imapHost;
    private final String user;
    private final String password;
    private final String imapPort;
    private final Properties properties;
    private final String protocol = "imaps";
    private final MailToEventProcessorService mailToEventProcessorService;


    public MailService(@Value("${imap.host}") String imapHost,
                       @Value("${imap.port}") String imapPort,
                       @Value("${imap.user}") String user,
                       @Value("${imap.password}") String password, MailToEventProcessorService mailToEventProcessorService) {
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.user = user;
        this.password = password;
        this.mailToEventProcessorService = mailToEventProcessorService;
        this.properties = getServerProperties();
        handleMailConnection();
    }

    private void handleMailConnection() {
        IMAPStore imapStore = login();

        try {
            Folder inbox = imapStore.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            Message[] messages = inbox.getMessages();
            inbox.addMessageCountListener(new MessageListener());
            for (Message message : messages) {
                mailToEventProcessorService.proceedMail(message);
            }

            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    while (inbox.isOpen()) {
                        //every 25 minutes poke the server with a inbox.getMessageCount() to keep the connection active/open
                        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
                        final Runnable pokeInbox = () -> {
                            try {
                                inbox.getMessageCount();
                                logger.info("Send keep alive to imap server");
                            } catch (MessagingException ignored) {
                            }
                        };
                        scheduler.schedule(pokeInbox, 25, TimeUnit.MINUTES);
                        try {
                            ((IMAPFolder) inbox).idle();
                        } catch (MessagingException e) {
                            logger.error("IMAP Idle", e);
                        }
                    }
                }
            };

            Executors.newSingleThreadExecutor().execute(runnable);

        } catch (MessagingException e) {
            logger.error("Handle Mail Connection", e);
        }
    }

    private IMAPStore login() {
        Session session = Session.getDefaultInstance(properties);
        try {
            Store store = session.getStore(protocol);
            store.connect(user, password);
            logger.info("Mail Client logged in");
            return (IMAPStore) store;
        } catch (NoSuchProviderException e) {
            logger.error("Getting session store for " + protocol, e);
        } catch (MessagingException e) {
            logger.error("Login with mail credentials", e);
            e.printStackTrace();
        }
        return null;
    }

    private Properties getServerProperties() {
        Properties properties = new Properties();

        // server setting
        properties.put(String.format("mail.%s.host", protocol), imapHost);
        properties.put(String.format("mail.%s.port", protocol), imapPort);

        // SSL setting
        properties.setProperty(
                String.format("mail.%s.socketFactory.class", protocol),
                "javax.net.ssl.SSLSocketFactory");
        properties.setProperty(
                String.format("mail.%s.socketFactory.fallback", protocol),
                "false");
        properties.setProperty(
                String.format("mail.%s.socketFactory.port", protocol),
                String.valueOf(imapPort));

        return properties;
    }

    private class MessageListener extends MessageCountAdapter {
        @Override
        public void messagesAdded(MessageCountEvent e) {
            for (Message message : e.getMessages()) {
                mailToEventProcessorService.proceedMail(message);
            }
        }
    }
}

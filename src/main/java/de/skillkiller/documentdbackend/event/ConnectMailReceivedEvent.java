package de.skillkiller.documentdbackend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import javax.mail.Message;

public class ConnectMailReceivedEvent extends ApplicationEvent {
    @Getter
    private final Message message;

    public ConnectMailReceivedEvent(Object source, Message message) {
        super(source);
        this.message = message;
    }
}

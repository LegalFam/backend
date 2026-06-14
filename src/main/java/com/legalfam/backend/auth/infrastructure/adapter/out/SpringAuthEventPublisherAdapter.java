package com.legalfam.backend.auth.infrastructure.adapter.out;

import com.legalfam.backend.auth.application.port.out.IAuthEventPublisherPort;
import com.legalfam.backend.common.event.UserRegisteredEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringAuthEventPublisherAdapter implements IAuthEventPublisherPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringAuthEventPublisherAdapter(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishUserRegistered(UserRegisteredEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}

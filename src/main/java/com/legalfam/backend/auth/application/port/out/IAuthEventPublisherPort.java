package com.legalfam.backend.auth.application.port.out;

import com.legalfam.backend.common.event.UserRegisteredEvent;

public interface IAuthEventPublisherPort {
    void publishUserRegistered(UserRegisteredEvent event);
}

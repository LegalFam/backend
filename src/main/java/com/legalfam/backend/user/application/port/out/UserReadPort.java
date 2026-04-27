package com.legalfam.backend.user.application.port.out;

import com.legalfam.backend.user.domain.model.User;
import java.util.List;

public interface UserReadPort {
    List<User> findAll();
}

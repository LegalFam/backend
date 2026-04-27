package com.legalfam.backend.user.application.port.in;

import com.legalfam.backend.user.application.dto.UserResponse;
import java.util.List;

public interface UserQueryUseCase {
    List<UserResponse> getAllUsers();
}

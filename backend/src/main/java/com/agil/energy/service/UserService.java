package com.agil.energy.service;

import com.agil.energy.dto.request.CreateUserRequest;
import com.agil.energy.dto.request.UpdateUserRequest;
import com.agil.energy.dto.response.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(Long id, UpdateUserRequest request);

    UserResponse getUserById(Long id);

    List<UserResponse> getAllUsers();

    void deactivateUser(Long id);

    void activateUser(Long id);

    void resetPassword(Long id, String newPassword);
}
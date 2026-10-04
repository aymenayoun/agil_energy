package com.agil.energy.controller;

import com.agil.energy.dto.request.CreateUserRequest;
import com.agil.energy.dto.request.UpdateUserRequest;
import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.dto.response.UserResponse;
import com.agil.energy.service.AuditService;
import com.agil.energy.service.UserService;
import com.agil.energy.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") //for admin(s) only
public class UserController {

    private final UserService userService;
    private final AuditService auditService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            HttpServletRequest httpRequest) {

        UserResponse response = userService.createUser(request);
        auditService.log(currentUser.getId(), "CREATE", "USER", response.getId(),
                null, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Utilisateur créé avec succès", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            HttpServletRequest httpRequest) {

        UserResponse response = userService.updateUser(id, request);
        auditService.log(currentUser.getId(), "UPDATE", "USER", id,
                null, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Utilisateur mis à jour", response));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            HttpServletRequest httpRequest) {

        userService.deactivateUser(id);
        auditService.log(currentUser.getId(), "DEACTIVATE", "USER", id,
                null, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Utilisateur désactivé", null));
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            HttpServletRequest httpRequest) {

        userService.activateUser(id);
        auditService.log(currentUser.getId(), "ACTIVATE", "USER", id,
                null, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Utilisateur activé", null));
    }

    @PutMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id,
            @RequestBody String newPassword,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            HttpServletRequest httpRequest) {

        userService.resetPassword(id, newPassword);
        auditService.log(currentUser.getId(), "RESET_PASSWORD", "USER", id,
                null, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Mot de passe réinitialisé", null));
    }
}
package com.agil.energy.controller;

import com.agil.energy.dto.request.CreateUserRequest;
import com.agil.energy.dto.request.UpdateUserRequest;
import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.dto.response.UserResponse;
import com.agil.energy.entity.Role;
import com.agil.energy.entity.User;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.security.CustomUserDetails;
import com.agil.energy.service.AuditService;
import com.agil.energy.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private UserService userService;
    @Mock private AuditService auditService;
    @InjectMocks private UserController controller;

    private CustomUserDetails currentUser;
    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        Role role = new Role(); role.setName("ADMIN");
        User user = new User();
        user.setId(1L); user.setName("Admin"); user.setEmail("admin@x.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(role);
        currentUser = new CustomUserDetails(user);
        httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("10.0.0.1");
    }

    @Test
    @DisplayName("createUser — returns 201 + audits CREATE")
    void createUser_returns201() {
        CreateUserRequest req = new CreateUserRequest();
        UserResponse mock = new UserResponse(); mock.setId(50L);
        when(userService.createUser(any())).thenReturn(mock);

        ResponseEntity<ApiResponse<UserResponse>> response =
                controller.createUser(req, currentUser, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(auditService).log(eq(1L), eq("CREATE"), eq("USER"), eq(50L), any(), anyString());
    }

    @Test
    @DisplayName("getAllUsers — returns 200 with list")
    void getAllUsers_returnsList() {
        when(userService.getAllUsers()).thenReturn(List.of(new UserResponse(), new UserResponse()));

        ResponseEntity<ApiResponse<List<UserResponse>>> response = controller.getAllUsers();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(2);
    }

    @Test
    @DisplayName("getUserById — returns 200")
    void getUserById_returnsUser() {
        UserResponse mock = new UserResponse(); mock.setId(7L);
        when(userService.getUserById(7L)).thenReturn(mock);

        ResponseEntity<ApiResponse<UserResponse>> response = controller.getUserById(7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("updateUser — returns 200 + audits UPDATE")
    void updateUser_returns200() {
        UpdateUserRequest req = new UpdateUserRequest();
        UserResponse mock = new UserResponse(); mock.setId(7L);
        when(userService.updateUser(eq(7L), any())).thenReturn(mock);

        ResponseEntity<ApiResponse<UserResponse>> response =
                controller.updateUser(7L, req, currentUser, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditService).log(eq(1L), eq("UPDATE"), eq("USER"), eq(7L), any(), anyString());
    }

    @Test
    @DisplayName("deactivateUser — returns 200")
    void deactivateUser_returns200() {
        ResponseEntity<ApiResponse<Void>> response =
                controller.deactivateUser(7L, currentUser, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).deactivateUser(7L);
        verify(auditService).log(eq(1L), eq("DEACTIVATE"), eq("USER"), eq(7L), any(), anyString());
    }

    @Test
    @DisplayName("activateUser — returns 200")
    void activateUser_returns200() {
        ResponseEntity<ApiResponse<Void>> response =
                controller.activateUser(7L, currentUser, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).activateUser(7L);
    }

    @Test
    @DisplayName("resetPassword — returns 200 + audits RESET_PASSWORD")
    void resetPassword_returns200() {
        ResponseEntity<ApiResponse<Void>> response =
                controller.resetPassword(7L, "newSecret123", currentUser, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).resetPassword(7L, "newSecret123");
        verify(auditService).log(eq(1L), eq("RESET_PASSWORD"), eq("USER"), eq(7L), any(), anyString());
    }
}
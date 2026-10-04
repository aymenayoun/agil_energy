package com.agil.energy.service;

import com.agil.energy.dto.request.CreateUserRequest;
import com.agil.energy.dto.request.UpdateUserRequest;
import com.agil.energy.dto.response.UserResponse;
import com.agil.energy.entity.Role;
import com.agil.energy.entity.User;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.exception.DuplicateResourceException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.RoleRepository;
import com.agil.energy.repository.StationRepository;
import com.agil.energy.repository.UserRepository;
import com.agil.energy.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private StationRepository stationRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EntityMapper mapper;

    @InjectMocks private UserServiceImpl userService;

    private User user;
    private Role role;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        role = new Role();
        role.setId(1L);
        role.setName("ADMIN");

        user = new User();
        user.setId(1L);
        user.setName("Admin");
        user.setEmail("admin@agil.tn");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);

        userResponse = new UserResponse();
        userResponse.setId(1L);
        userResponse.setName("Admin");
    }

    @Test
    @DisplayName("createUser — creates user with encoded password")
    void createUser_success() {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("Admin");
        request.setEmail("admin@agil.tn");
        request.setPassword("password123");
        request.setRoleName("ADMIN");

        when(userRepository.existsByEmail("admin@agil.tn")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(mapper.toUserResponse(any())).thenReturn(userResponse);

        UserResponse result = userService.createUser(request);

        assertThat(result.getName()).isEqualTo("Admin");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("createUser — throws DuplicateResourceException for existing email")
    void createUser_duplicateEmail_throwsException() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("admin@agil.tn");

        when(userRepository.existsByEmail("admin@agil.tn")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("createUser — throws BusinessException for unknown role")
    void createUser_unknownRole_throwsException() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new@agil.tn");
        request.setRoleName("FAKE_ROLE");

        when(userRepository.existsByEmail("new@agil.tn")).thenReturn(false);
        when(roleRepository.findByName("FAKE_ROLE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("getUserById — throws ResourceNotFoundException for unknown ID")
    void getUserById_unknown_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deactivateUser — sets status to INACTIVE")
    void deactivateUser_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deactivateUser(1L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("resetPassword — encodes and saves new password")
    void resetPassword_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass")).thenReturn("newHash");

        userService.resetPassword(1L, "newPass");

        assertThat(user.getPasswordHash()).isEqualTo("newHash");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateUser — throws DuplicateResourceException on email conflict")
    void updateUser_emailConflict_throwsException() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("taken@agil.tn");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@agil.tn")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateUser(1L, request))
                .isInstanceOf(DuplicateResourceException.class);
    }
}
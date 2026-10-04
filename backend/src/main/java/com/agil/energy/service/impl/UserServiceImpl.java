package com.agil.energy.service.impl;

import com.agil.energy.dto.request.CreateUserRequest;
import com.agil.energy.dto.request.UpdateUserRequest;
import com.agil.energy.dto.response.UserResponse;
import com.agil.energy.entity.Role;
import com.agil.energy.entity.Station;
import com.agil.energy.entity.User;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.exception.DuplicateResourceException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.RoleRepository;
import com.agil.energy.repository.StationRepository;
import com.agil.energy.repository.UserRepository;
import com.agil.energy.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private static final String ROLE_STATION_MANAGER = "STATION_MANAGER";
    private static final String ROLE_MANAGER         = "MANAGER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StationRepository stationRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityMapper mapper;

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Un utilisateur avec cet email existe déjà: " + request.getEmail());
        }

        Role role = roleRepository.findByName(request.getRoleName())
                .orElseThrow(() -> new BusinessException("Rôle inconnu: " + request.getRoleName()));

        Station station = resolveStationForRole(role.getName(), request.getStationId(), null);
        String region   = resolveRegionForRole(role.getName(), request.getRegion());

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .station(station)
                .region(region)
                .status(UserStatus.ACTIVE)
                .build();

        return mapper.toUserResponse(userRepository.save(user));
    }

    @Override
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));

        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Un utilisateur avec cet email existe déjà: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }
        if (request.getRoleName() != null) {
            Role role = roleRepository.findByName(request.getRoleName())
                    .orElseThrow(() -> new BusinessException("Rôle inconnu: " + request.getRoleName()));
            user.setRole(role);
        }

        String finalRoleName = user.getRole().getName();

        // ---- Station assignment (for STATION_MANAGER) ----
        boolean stationChanged = request.getStationId() != null
                || (ROLE_STATION_MANAGER.equals(finalRoleName) && user.getStation() == null);
        if (stationChanged) {
            Station station = resolveStationForRole(finalRoleName, request.getStationId(), user.getId());
            user.setStation(station);
        } else if (!ROLE_STATION_MANAGER.equals(finalRoleName)) {
            user.setStation(null);
        }

        // ---- Region assignment (for MANAGER) ----
        boolean regionChanged = request.getRegion() != null
                || (ROLE_MANAGER.equals(finalRoleName) && user.getRegion() == null);
        if (regionChanged) {
            String region = resolveRegionForRole(finalRoleName,
                    request.getRegion() != null ? request.getRegion() : user.getRegion());
            user.setRegion(region);
        } else if (!ROLE_MANAGER.equals(finalRoleName)) {
            user.setRegion(null);
        }

        return mapper.toUserResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        return mapper.toUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(mapper::toUserResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
    }

    @Override
    public void activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    // ---------- helpers ----------

    /**
     * STATION_MANAGER ⇒ stationId required & must point to an existing station
     * which is not already covered by another active STATION_MANAGER.
     * Any other role ⇒ stationId must be null.
     */
    private Station resolveStationForRole(String roleName, Long stationId, Long userBeingEditedId) {
        if (ROLE_STATION_MANAGER.equals(roleName)) {
            if (stationId == null) {
                throw new BusinessException("Une station doit être assignée à un gérant de station");
            }
            Station station = stationRepository.findById(stationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Station", stationId));

            Optional<User> existing = userRepository.findFirstByStationIdAndRoleNameAndStatus(
                    stationId, ROLE_STATION_MANAGER, UserStatus.ACTIVE);
            if (existing.isPresent() && !existing.get().getId().equals(userBeingEditedId)) {
                throw new BusinessException(
                        "Cette station est déjà gérée par " + existing.get().getName() +
                                ". Désactivez d'abord ce gérant avant d'en assigner un nouveau.");
            }
            return station;
        }
        if (stationId != null) {
            throw new BusinessException("Une station ne peut être assignée qu'à un gérant de station");
        }
        return null;
    }

    /**
     * MANAGER ⇒ region required & must match at least one existing station.
     * Any other role ⇒ region must be null.
     */
    private String resolveRegionForRole(String roleName, String region) {
        if (ROLE_MANAGER.equals(roleName)) {
            if (region == null || region.isBlank()) {
                throw new BusinessException("Une région doit être assignée à un gestionnaire régional");
            }
            List<?> stationsInRegion = stationRepository.findByRegion(region);
            if (stationsInRegion.isEmpty()) {
                throw new BusinessException("Aucune station trouvée dans la région: " + region);
            }
            return region;
        }
        if (region != null && !region.isBlank()) {
            throw new BusinessException("Une région ne peut être assignée qu'à un gestionnaire (MANAGER)");
        }
        return null;
    }
}
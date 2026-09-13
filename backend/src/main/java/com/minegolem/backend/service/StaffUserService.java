package com.minegolem.backend.service;

import com.minegolem.backend.domain.entity.Gym;
import com.minegolem.backend.domain.entity.Permission;
import com.minegolem.backend.domain.entity.Role;
import com.minegolem.backend.domain.entity.StaffUser;
import com.minegolem.backend.dto.request.StaffUserRequest;
import com.minegolem.backend.dto.response.RoleResponse;
import com.minegolem.backend.dto.response.StaffUserResponse;
import com.minegolem.backend.repository.GymRepository;
import com.minegolem.backend.repository.RoleRepository;
import com.minegolem.backend.repository.StaffUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffUserService {

    private final StaffUserRepository staffUserRepository;
    private final RoleRepository roleRepository;
    private final GymRepository gymRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<StaffUserResponse> listByGym(UUID gymId) {
        return staffUserRepository.findAll().stream()
            .filter(u -> u.getGym() != null && u.getGym().getId().equals(gymId))
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
            .map(r -> new RoleResponse(
                r.getId(),
                r.getName(),
                r.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet())
            ))
            .toList();
    }

    @Transactional
    public StaffUserResponse create(UUID gymId, StaffUserRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("La password è obbligatoria per i nuovi account");
        }
        if (staffUserRepository.existsByEmail(request.email().trim().toLowerCase())) {
            throw new IllegalArgumentException("Un operatore con questa email esiste già");
        }

        Gym gym = gymRepository.findById(gymId)
            .orElseThrow(() -> new IllegalArgumentException("Palestra non trovata"));

        Role role = roleRepository.findById(request.roleId())
            .orElseThrow(() -> new IllegalArgumentException("Ruolo non valido"));

        StaffUser staffUser = StaffUser.builder()
            .gym(gym)
            .email(request.email().trim().toLowerCase())
            .passwordHash(passwordEncoder.encode(request.password()))
            .firstName(request.firstName().trim())
            .lastName(request.lastName() != null ? request.lastName().trim() : "")
            .role(role)
            .active(request.active() != null ? request.active() : true)
            .build();

        StaffUser saved = staffUserRepository.save(staffUser);
        return toResponse(saved);
    }

    @Transactional
    public StaffUserResponse update(UUID staffUserId, UUID gymId, StaffUserRequest request) {
        StaffUser staffUser = staffUserRepository.findById(staffUserId)
            .orElseThrow(() -> new IllegalArgumentException("Operatore non trovato"));

        if (!staffUser.getGym().getId().equals(gymId)) {
            throw new IllegalArgumentException("Operazione non autorizzata per questa palestra");
        }

        String normalizedEmail = request.email().trim().toLowerCase();
        if (!staffUser.getEmail().equalsIgnoreCase(normalizedEmail) && staffUserRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Un operatore con questa email esiste già");
        }

        Role role = roleRepository.findById(request.roleId())
            .orElseThrow(() -> new IllegalArgumentException("Ruolo non valido"));

        staffUser.setEmail(normalizedEmail);
        staffUser.setFirstName(request.firstName().trim());
        staffUser.setLastName(request.lastName() != null ? request.lastName().trim() : "");
        staffUser.setRole(role);

        if (request.active() != null) {
            staffUser.setActive(request.active());
        }

        if (request.password() != null && !request.password().isBlank()) {
            staffUser.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        StaffUser saved = staffUserRepository.save(staffUser);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID staffUserId, UUID gymId) {
        StaffUser staffUser = staffUserRepository.findById(staffUserId)
            .orElseThrow(() -> new IllegalArgumentException("Operatore non trovato"));

        if (!staffUser.getGym().getId().equals(gymId)) {
            throw new IllegalArgumentException("Operazione non autorizzata per questa palestra");
        }

        staffUserRepository.delete(staffUser);
    }

    private StaffUserResponse toResponse(StaffUser u) {
        return new StaffUserResponse(
            u.getId(),
            u.getGym().getId(),
            u.getEmail(),
            u.getFirstName(),
            u.getLastName(),
            u.getFullName(),
            u.getRole().getId(),
            u.getRole().getName(),
            u.getPermissionNames().stream().collect(Collectors.toSet()),
            u.isActive(),
            u.getLastLoginAt(),
            u.getCreatedAt()
        );
    }
}

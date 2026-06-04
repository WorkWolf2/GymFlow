package com.minegolem.backend.controller;


import com.minegolem.backend.domain.entity.StaffUser;
import com.minegolem.backend.dto.request.LoginRequest;
import com.minegolem.backend.dto.response.AuthResponse;
import com.minegolem.backend.repository.StaffUserRepository;
import com.minegolem.backend.security.JwtService;
import com.minegolem.backend.security.StaffUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final StaffUserRepository staffUserRepository;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        StaffUserDetails userDetails = (StaffUserDetails) auth.getPrincipal();

        // update last login
        staffUserRepository.findByEmailAndActiveTrue(request.email())
            .ifPresent(u -> {
                u.setLastLoginAt(LocalDateTime.now());
                staffUserRepository.save(u);
            });

        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        String fullName = staffUserRepository.findByEmailAndActiveTrue(request.email())
            .map(StaffUser::getFullName).orElse("");

        return ResponseEntity.ok(AuthResponse.of(accessToken, refreshToken, 86400000L, userDetails, fullName));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestHeader("Authorization") String bearerToken) {
        String token = bearerToken.substring(7);
        String email = jwtService.extractUsername(token);

        StaffUser staffUser = staffUserRepository.findByEmailAndActiveTrue(email)
            .orElseThrow();
        StaffUserDetails userDetails = new StaffUserDetails(staffUser);

        if (jwtService.isTokenValid(token, userDetails)) {
            String newAccess = jwtService.generateToken(userDetails);
            String newRefresh = jwtService.generateRefreshToken(userDetails);
            return ResponseEntity.ok(AuthResponse.of(newAccess, newRefresh, 86400000L, userDetails, staffUser.getFullName()));
        }
        return ResponseEntity.status(401).build();
    }
}

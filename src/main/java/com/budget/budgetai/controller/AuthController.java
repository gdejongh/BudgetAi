package com.budget.budgetai.controller;

import com.budget.budgetai.config.SecurityUtils;
import com.budget.budgetai.dto.AuthResponseDTO;
import com.budget.budgetai.dto.LoginRequestDTO;
import com.budget.budgetai.dto.RefreshRequestDTO;
import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.model.RefreshToken;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AppUserRepository appUserRepository;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
            AppUserRepository appUserRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        AppUser appUser = appUserRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String accessToken = jwtService.generateAccessToken(appUser.getId(), appUser.getEmail());
        String refreshToken = jwtService.generateRefreshToken(appUser.getId());

        return ResponseEntity.ok(new AuthResponseDTO(accessToken, refreshToken, appUser.getId(), appUser.getEmail()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO refreshRequest) {
        RefreshToken refreshToken = jwtService.validateRefreshToken(refreshRequest.getRefreshToken());
        AppUser appUser = refreshToken.getAppUser();

        // Rotate: delete old refresh token, issue new pair
        jwtService.revokeRefreshToken(refreshRequest.getRefreshToken());

        String newAccessToken = jwtService.generateAccessToken(appUser.getId(), appUser.getEmail());
        String newRefreshToken = jwtService.generateRefreshToken(appUser.getId());

        return ResponseEntity
                .ok(new AuthResponseDTO(newAccessToken, newRefreshToken, appUser.getId(), appUser.getEmail()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        java.util.UUID userId = SecurityUtils.getCurrentUserId();
        jwtService.revokeAllUserRefreshTokens(userId);
        return ResponseEntity.noContent().build();
    }
}

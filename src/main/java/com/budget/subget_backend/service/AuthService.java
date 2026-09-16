package com.budget.subget_backend.service;

import com.budget.subget_backend.dto.request.LoginRequest;
import com.budget.subget_backend.dto.request.RegisterRequest;
import com.budget.subget_backend.dto.response.AuthResponse;
import com.budget.subget_backend.entity.Role;
import com.budget.subget_backend.entity.User;
import com.budget.subget_backend.exception.DuplicateResourceException;
import com.budget.subget_backend.repository.UserRepository;
import com.budget.subget_backend.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Cet email est déjà utilisé");
        }

        User user = User.builder()
                .nom(request.getNom())
                .email(request.getEmail())
                .motDePasse(passwordEncoder.encode(request.getMotDePasse()))
                .role(Role.CLIENT) // toujours CLIENT via cet endpoint public
                .actif(true)
                .build();

        userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        // Déclenche BadCredentialsException si invalide -> géré par GlobalExceptionHandler
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getMotDePasse()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(); // ne devrait pas arriver si authenticate() a réussi

        String accessToken = jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail(), user.getRole().name());

        return new AuthResponse(accessToken, refreshToken);
    }

    public AuthResponse refresh(String refreshToken) {
        // isRefreshToken() vérifie la validité ET le type : un access token
        // présenté ici est rejeté, il ne doit pas permettre de regénérer
        // indéfiniment de nouveaux tokens.
        if (!jwtUtils.isRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Token invalide");
        }
        String email = jwtUtils.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email).orElseThrow();

        String newAccessToken = jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name());
        String newRefreshToken = jwtUtils.generateRefreshToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(newAccessToken, newRefreshToken);
    }
}

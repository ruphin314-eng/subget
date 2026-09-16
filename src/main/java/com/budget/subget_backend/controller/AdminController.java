package com.budget.subget_backend.controller;

import com.budget.subget_backend.dto.response.UserResponse;
import com.budget.subget_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    // Défense en profondeur : SecurityConfig restreint déjà /admin/** au rôle
    // ADMIN, cette vérification explicite au niveau controller est redondante
    // mais volontaire.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(userService.listAllUsers());
    }
}

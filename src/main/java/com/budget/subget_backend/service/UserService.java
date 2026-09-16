package com.budget.subget_backend.service;

import com.budget.subget_backend.dto.request.UpdateUserRequest;
import com.budget.subget_backend.dto.response.UserResponse;
import com.budget.subget_backend.entity.User;
import com.budget.subget_backend.exception.ResourceNotFoundException;
import com.budget.subget_backend.repository.UserRepository;
import com.budget.subget_backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    public UserResponse getMe() {
        User user = findById(currentUser.id());
        return UserResponse.fromEntity(user);
    }

    // Utiliser par les deux rôles : un client modifie ses infos, un admin les siennes.
    // La cible est toujours currentUser.id() - jamais un id fourni par le client.
    public UserResponse updateMe(UpdateUserRequest request) {
        User user = findById(currentUser.id());

        if (request.getNom() != null) user.setNom(request.getNom());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getMotDePasse() != null && !request.getMotDePasse().isBlank()) {
            user.setMotDePasse(passwordEncoder.encode(request.getMotDePasse()));
        }

        return UserResponse.fromEntity(userRepository.save(user));
    }

    // Réservé à l'ADMIN (vérifié via @PreAuthorize au niveau du controller).
    public List<UserResponse> listAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}

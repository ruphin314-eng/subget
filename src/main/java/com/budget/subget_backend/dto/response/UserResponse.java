package com.budget.subget_backend.dto.response;

import com.budget.subget_backend.entity.Role;
import com.budget.subget_backend.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private String nom;
    private String email;
    private Role role;
    private boolean actif;

    // Ne mappe jamais motDePasse : ce DTO est la seule vue exposée au client.
    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .nom(user.getNom())
                .email(user.getEmail())
                .role(user.getRole())
                .actif(user.isActif())
                .build();
    }
}

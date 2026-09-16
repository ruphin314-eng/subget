package com.budget.subget_backend.dto.request;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {
    private String nom;

    @Email
    private String email;

    // Optionnel : si présent, sera re-hashé avant sauvegarde.
    private String motDePasse;
}

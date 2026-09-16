package com.budget.subget_backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank
    private String nom;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    // Au moins 8 caractères, une majuscule, une minuscule, un chiffre et un
    // caractère spécial. Le message reste générique côté client (pas de détail
    // sur quelle règle précise a échoué) pour ne pas aider un attaquant à
    // deviner la politique exacte, mais reste assez clair pour un utilisateur légitime.
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!?_-]).{8,}$",
            message = "Le mot de passe doit contenir au moins 8 caractères, une majuscule, une minuscule, un chiffre et un caractère spécial"
    )
    private String motDePasse;
    // Le rôle n'est volontairement pas un champ ici : l'inscription publique
    // crée toujours un CLIENT. Le rôle ADMIN n'est jamais assignable via cet endpoint.
}

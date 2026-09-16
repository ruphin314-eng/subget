package com.budget.subget_backend.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Point d'accès unique à l'identité de l'utilisateur authentifié.
 * Les services doivent TOUJOURS passer par ici pour déterminer le userId
 * concerné par une opération - jamais accepter un userId venant du body
 * ou d'un paramètre de requête envoyé par le client (protection anti-IDOR).
 */
@Component
public class CurrentUser {

    public CustomUserDetails get() {
        return (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    public Long id() {
        return get().getId();
    }
}

package com.budget.subget_backend.entity;

/**
 * Rôles applicatifs.
 * Un seul ADMIN (super admin) créé en base pour l'instant.
 * Prévu pour être étendu (ex: SUPER_ADMIN vs ADMIN) sans casser le schéma.
 */
public enum Role {
    CLIENT,
    ADMIN
}

package com.budget.subget_backend.security;

import com.budget.subget_backend.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// Adapte notre entité User au contrat UserDetails attendu par Spring Security,
// pour pouvoir l'utiliser directement dans le contexte d'authentification.
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String motDePasse;
    private final boolean actif;
    private final Collection<? extends GrantedAuthority> authorities;

    // Construit le UserDetails à partir de l'entité User, en convertissant
    // le rôle métier (ex: ADMIN) en autorité Spring Security (ex: ROLE_ADMIN).
    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.motDePasse = user.getMotDePasse();
        this.actif = user.isActif();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    // Mot de passe (haché) utilisé par Spring Security pour vérifier les identifiants.
    @Override
    public String getPassword() { return motDePasse; }

    // Identifiant de connexion : ici l'email sert de "username".
    @Override
    public String getUsername() { return email; }

    // Pas de gestion d'expiration de compte -> toujours considéré comme non expiré.
    @Override
    public boolean isAccountNonExpired() { return true; }

    // Un compte inactif (actif=false) est traité comme verrouillé, ce qui bloque la connexion.
    @Override
    public boolean isAccountNonLocked() { return actif; }

    // Pas de gestion d'expiration des identifiants -> toujours considérés comme valides.
    @Override
    public boolean isCredentialsNonExpired() { return true; }

    // Un compte inactif (actif=false) est aussi considéré comme désactivé,
    // ce qui déclenche une DisabledException lors de l'authentification.
    @Override
    public boolean isEnabled() { return actif; }
}

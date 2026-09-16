package com.budget.subget_backend.config;

import com.budget.subget_backend.entity.Role;
import com.budget.subget_backend.entity.User;
import com.budget.subget_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée le compte super admin au démarrage s'il n'existe pas encore.
 * Idempotent : ne fait rien si un utilisateur ADMIN avec cet email existe déjà.
 *
 * Le mot de passe est hashé ici, jamais stocké ni loggé en clair
 * (cahier des charges 5.4 : "mot de passe déjà hashé avant insertion").
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.email}")
    private String adminEmail;

    @Value("${admin.seed.password}")
    private String adminPassword;

    @Value("${admin.seed.nom:Super Admin}")
    private String adminNom;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Compte admin déjà présent, seed ignoré.");
            return;
        }

        User admin = User.builder()
                .nom(adminNom)
                .email(adminEmail)
                .motDePasse(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .actif(true)
                .build();

        userRepository.save(admin);
        // On ne logue jamais le mot de passe, même hashé.
        log.info("Compte super admin créé : {}", adminEmail);
    }
}

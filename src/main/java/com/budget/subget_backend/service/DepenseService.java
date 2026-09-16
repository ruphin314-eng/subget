package com.budget.subget_backend.service;

import com.budget.subget_backend.dto.request.MouvementRequest;
import com.budget.subget_backend.entity.Depense;
import com.budget.subget_backend.entity.User;
import com.budget.subget_backend.exception.ResourceNotFoundException;
import com.budget.subget_backend.repository.DepenseRepository;
import com.budget.subget_backend.repository.UserRepository;
import com.budget.subget_backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepenseService {

    private final DepenseRepository depenseRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public Depense create(MouvementRequest request) {
        User user = userRepository.getReferenceById(currentUser.id());

        Depense depense = Depense.builder()
                .user(user) // toujours l'utilisateur authentifié, jamais un id du body
                .montant(request.getMontant())
                .categorie(request.getLibelle())
                .description(request.getDescription())
                .date(request.getDate())
                .build();

        return depenseRepository.save(depense);
    }

    public List<Depense> listForCurrentUser(LocalDate from, LocalDate to) {
        return depenseRepository.findByUserIdAndDateBetween(currentUser.id(), from, to);
    }

    // Vérifie systématiquement que la dépense appartient à l'utilisateur
    // authentifié avant toute lecture/modification/suppression (anti-IDOR).
    public Depense getOwnedOrThrow(Long depenseId) {
        Depense depense = depenseRepository.findById(depenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Dépense introuvable"));

        if (!depense.getUser().getId().equals(currentUser.id())) {
            throw new ResourceNotFoundException("Dépense introuvable");
            // Volontairement "introuvable" plutôt que "accès refusé" :
            // ne pas confirmer à un utilisateur que l'id existe chez un autre.
        }
        return depense;
    }
}

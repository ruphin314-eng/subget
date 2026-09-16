package com.budget.subget_backend.service;

import com.budget.subget_backend.dto.request.MouvementRequest;
import com.budget.subget_backend.entity.Entree;
import com.budget.subget_backend.entity.User;
import com.budget.subget_backend.exception.ResourceNotFoundException;
import com.budget.subget_backend.repository.EntreeRepository;
import com.budget.subget_backend.repository.UserRepository;
import com.budget.subget_backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EntreeService {

    private final EntreeRepository entreeRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public Entree create(MouvementRequest request) {
        User user = userRepository.getReferenceById(currentUser.id());

        Entree entree = Entree.builder()
                .user(user)
                .montant(request.getMontant())
                .source(request.getLibelle())
                .description(request.getDescription())
                .date(request.getDate())
                .build();

        return entreeRepository.save(entree);
    }

    public List<Entree> listForCurrentUser(LocalDate from, LocalDate to) {
        return entreeRepository.findByUserIdAndDateBetween(currentUser.id(), from, to);
    }

    public Entree getOwnedOrThrow(Long entreeId) {
        Entree entree = entreeRepository.findById(entreeId)
                .orElseThrow(() -> new ResourceNotFoundException("Entrée introuvable"));

        if (!entree.getUser().getId().equals(currentUser.id())) {
            throw new ResourceNotFoundException("Entrée introuvable");
        }
        return entree;
    }
}

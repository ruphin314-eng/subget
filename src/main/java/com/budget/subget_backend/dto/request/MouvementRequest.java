package com.budget.subget_backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO commun pour créer une Dépénse ou une Entreé.
 * Le champ "categorie" (dépense) et "source" (entrée) partagent ce champ
 * générique "libelle" pour simplifier - à séparer si les règles métier divergent.
 */
@Getter
@Setter
public class MouvementRequest {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "Le montant doit être positif")
    private BigDecimal montant;

    @NotBlank
    private String libelle; // catégorie pour une dépense, source pour une entrée

    private String description;

    @NotNull
    private LocalDate date;
}

package com.budget.subget_backend.dto.response;

import com.budget.subget_backend.entity.Depense;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class DepenseResponse {
    private Long id;
    private BigDecimal montant;
    private String categorie;
    private String description;
    private LocalDate date;

    public static DepenseResponse fromEntity(Depense depense) {
        return DepenseResponse.builder()
                .id(depense.getId())
                .montant(depense.getMontant())
                .categorie(depense.getCategorie())
                .description(depense.getDescription())
                .date(depense.getDate())
                .build();
    }
}

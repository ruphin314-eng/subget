package com.budget.subget_backend.dto.response;

import com.budget.subget_backend.entity.Entree;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class EntreeResponse {
    private Long id;
    private BigDecimal montant;
    private String source;
    private String description;
    private LocalDate date;

    public static EntreeResponse fromEntity(Entree entree) {
        return EntreeResponse.builder()
                .id(entree.getId())
                .montant(entree.getMontant())
                .source(entree.getSource())
                .description(entree.getDescription())
                .date(entree.getDate())
                .build();
    }
}

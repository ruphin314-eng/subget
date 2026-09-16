package com.budget.subget_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "entrees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entree {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ne JAMAIS renseigner cette relation depuis une valeur envoyée par le
    // client : toujours déduite de l'utilisateur authentifié (JWT).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private BigDecimal montant;

    @Column(nullable = false)
    private String source;

    private String description;

    @Column(nullable = false)
    private LocalDate date;
}

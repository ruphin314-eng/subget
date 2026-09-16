package com.budget.subget_backend.repository;

import com.budget.subget_backend.entity.Depense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DepenseRepository extends JpaRepository<Depense, Long> {

    // Toujours filtrer par userId : un client ne doit jamais voir les
    // dépenses d'un autre utilisateur.
    List<Depense> findByUserIdAndDateBetween(Long userId, LocalDate from, LocalDate to);

    @Query("SELECT COUNT(d) FROM Depense d WHERE d.user.id = :userId AND d.date BETWEEN :from AND :to")
    long countByUserAndPeriode(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(d.montant), 0) FROM Depense d WHERE d.user.id = :userId AND d.date BETWEEN :from AND :to")
    BigDecimal sumByUserAndPeriode(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}

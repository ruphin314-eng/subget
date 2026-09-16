package com.budget.subget_backend.repository;

import com.budget.subget_backend.entity.Entree;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface EntreeRepository extends JpaRepository<Entree, Long> {

    List<Entree> findByUserIdAndDateBetween(Long userId, LocalDate from, LocalDate to);

    @Query("SELECT COUNT(e) FROM Entree e WHERE e.user.id = :userId AND e.date BETWEEN :from AND :to")
    long countByUserAndPeriode(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(e.montant), 0) FROM Entree e WHERE e.user.id = :userId AND e.date BETWEEN :from AND :to")
    BigDecimal sumByUserAndPeriode(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}

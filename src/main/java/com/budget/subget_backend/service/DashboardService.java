package com.budget.subget_backend.service;

import com.budget.subget_backend.dto.response.DashboardResponse;
import com.budget.subget_backend.repository.DepenseRepository;
import com.budget.subget_backend.repository.EntreeRepository;
import com.budget.subget_backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DepenseRepository depenseRepository;
    private final EntreeRepository entreeRepository;
    private final CurrentUser currentUser;

    public DashboardResponse getDashboard(LocalDate from, LocalDate to) {
        Long userId = currentUser.id();

        long nombreDepenses = depenseRepository.countByUserAndPeriode(userId, from, to);
        BigDecimal totalDepenses = depenseRepository.sumByUserAndPeriode(userId, from, to);

        long nombreEntrees = entreeRepository.countByUserAndPeriode(userId, from, to);
        BigDecimal totalEntrees = entreeRepository.sumByUserAndPeriode(userId, from, to);

        BigDecimal solde = totalEntrees.subtract(totalDepenses);

        return new DashboardResponse(nombreDepenses, totalDepenses, nombreEntrees, totalEntrees, solde);
    }
}

package com.budget.subget_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class DashboardResponse {
    private long nombreDepenses;
    private BigDecimal totalDepenses;
    private long nombreEntrees;
    private BigDecimal totalEntrees;
    private BigDecimal solde;
}

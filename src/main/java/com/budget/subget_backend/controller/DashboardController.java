package com.budget.subget_backend.controller;

import com.budget.subget_backend.dto.response.DashboardResponse;
import com.budget.subget_backend.service.DashboardService;
import com.budget.subget_backend.util.PeriodeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(@RequestParam(required = false) String periode) {
        var plage = PeriodeResolver.resolve(periode, null, null);
        return ResponseEntity.ok(dashboardService.getDashboard(plage.from(), plage.to()));
    }
}

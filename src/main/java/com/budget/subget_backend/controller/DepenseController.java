package com.budget.subget_backend.controller;

import com.budget.subget_backend.dto.request.MouvementRequest;
import com.budget.subget_backend.dto.response.DepenseResponse;
import com.budget.subget_backend.service.DepenseService;
import com.budget.subget_backend.util.PeriodeResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/depenses")
@RequiredArgsConstructor
public class DepenseController {

    private final DepenseService depenseService;

    @PostMapping
    public ResponseEntity<DepenseResponse> create(@Valid @RequestBody MouvementRequest request) {
        var depense = depenseService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DepenseResponse.fromEntity(depense));
    }

    @GetMapping
    public ResponseEntity<List<DepenseResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String periode) {

        var plage = PeriodeResolver.resolve(periode, from, to);
        var depenses = depenseService.listForCurrentUser(plage.from(), plage.to())
                .stream()
                .map(DepenseResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(depenses);
    }
}

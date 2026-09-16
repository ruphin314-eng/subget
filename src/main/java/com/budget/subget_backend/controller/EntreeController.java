package com.budget.subget_backend.controller;

import com.budget.subget_backend.dto.request.MouvementRequest;
import com.budget.subget_backend.dto.response.EntreeResponse;
import com.budget.subget_backend.service.EntreeService;
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
@RequestMapping("/entrees")
@RequiredArgsConstructor
public class EntreeController {

    private final EntreeService entreeService;

    @PostMapping
    public ResponseEntity<EntreeResponse> create(@Valid @RequestBody MouvementRequest request) {
        var entree = entreeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(EntreeResponse.fromEntity(entree));
    }

    @GetMapping
    public ResponseEntity<List<EntreeResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String periode) {

        var plage = PeriodeResolver.resolve(periode, from, to);
        var entrees = entreeService.listForCurrentUser(plage.from(), plage.to())
                .stream()
                .map(EntreeResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(entrees);
    }
}

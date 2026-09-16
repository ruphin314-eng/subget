package com.budget.subget_backend.util;

import java.time.LocalDate;
import java.time.YearMonth;

public final class PeriodeResolver {

    public record Plage(LocalDate from, LocalDate to) {}

    private PeriodeResolver() {}

    // Priorité aux bornes explicites (from/to) si fournies ; sinon dérive la
    // plage depuis le paramètre "periode" (jour|mois) par rapport à aujourd'hui.
    public static Plage resolve(String periode, LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return new Plage(from, to);
        }

        LocalDate today = LocalDate.now();

        if ("mois".equalsIgnoreCase(periode)) {
            YearMonth ym = YearMonth.from(today);
            return new Plage(ym.atDay(1), ym.atEndOfMonth());
        }

        // "jour" par défaut si periode absent ou inconnu.
        return new Plage(today, today);
    }
}

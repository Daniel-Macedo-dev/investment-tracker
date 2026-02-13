package com.daniel.domain;

import java.time.LocalDate;
import java.util.Map;

public record DailySummary(
        LocalDate date,
        long totalTodayCents,
        long totalProfitTodayCents,
        long cashTodayCents,
        long cashProfitTodayCents,
        Map<Long, Long> investmentTodayCents,
        Map<Long, Long> investmentProfitTodayCents
) {}

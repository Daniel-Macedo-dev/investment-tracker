package com.daniel.domain;

import java.time.LocalDate;
import java.util.Map;

public record DailyEntry(
        LocalDate date,
        long cashCents,
        Map<Long, Long> investmentValuesCents
) {}

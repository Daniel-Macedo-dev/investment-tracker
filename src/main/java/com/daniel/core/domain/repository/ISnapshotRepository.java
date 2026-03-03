package com.daniel.core.domain.repository;

import java.time.LocalDate;
import java.util.Map;

public interface ISnapshotRepository {
    long getCash(LocalDate date);
    void setCash(LocalDate date);
    Map<Long, Long> getAllInvestimentsForDate (LocalDate date);
    void setInvestimentValue(LocalDate date, long typeId, long cents);
    Map<String, Long> seriesForInvestiments(long investimentsTypeId);
}

package com.daniel.core.domain.repository;

import com.daniel.core.domain.entity.InvestmentType;

import java.util.List;

public interface IInvestmentTypeRepository {
    List<InvestmentType> listAll();
    void save(InvestmentType type);
    void delete(long id);
}

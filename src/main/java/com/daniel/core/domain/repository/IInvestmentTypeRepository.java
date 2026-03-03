package com.daniel.core.domain.repository;

import com.daniel.core.domain.entity.InvestmentType;

import java.util.List;

public interface IInvestmentTypeRepository {
    List<InvestmentType> listAll();
    void save(String name);
    void rename(int id, String newName);
    void delete(long id);
}

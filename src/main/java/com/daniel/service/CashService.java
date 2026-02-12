package com.daniel.service;

import com.daniel.domain.TransactionType;
import com.daniel.repository.TransactionRepository;

import java.time.Instant;

public final class CashService {
    private final TransactionRepository txRepo;

    public CashService(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    public void deposit(long cashAccountId, long amountCents, String note) {
        requirePositive(amountCents);
        txRepo.insert(TransactionType.DEPOSIT, cashAccountId, amountCents, Instant.now(), note);
    }

    public void withdraw(long cashAccountId, long amountCents, String note) {
        requirePositive(amountCents);

        long current = txRepo.computeCashBalanceCents(cashAccountId);
        if (amountCents > current) {
            throw new IllegalArgumentException("Saldo insuficiente para retirada.");
        }

        txRepo.insert(TransactionType.WITHDRAW, cashAccountId, amountCents, Instant.now(), note);
    }

    private static void requirePositive(long amountCents) {
        if (amountCents <= 0) throw new IllegalArgumentException("Valor precisa ser maior que zero.");
    }
}

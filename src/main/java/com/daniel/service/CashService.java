package com.daniel.service;

import com.daniel.repository.TransactionRepository;

import java.time.Instant;

public final class CashService {
    private final TransactionRepository txRepo;

    public CashService(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    public void deposit(long cashAccountId, long amountCents, String note) {
        requirePositive(amountCents);
        txRepo.insertDeposit(cashAccountId, amountCents, Instant.now(), note);
    }

    public void withdraw(long cashAccountId, long amountCents, String note) {
        requirePositive(amountCents);
        long current = txRepo.balanceCents(cashAccountId);
        if (amountCents > current) throw new IllegalArgumentException("Saldo insuficiente.");
        txRepo.insertWithdraw(cashAccountId, amountCents, Instant.now(), note);
    }

    public void transfer(long fromAccountId, long toAccountId, long amountCents, String note) {
        requirePositive(amountCents);
        long current = txRepo.balanceCents(fromAccountId);
        if (amountCents > current) throw new IllegalArgumentException("Saldo insuficiente para transferir.");
        txRepo.insertTransfer(fromAccountId, toAccountId, amountCents, Instant.now(), note);
    }

    private static void requirePositive(long amountCents) {
        if (amountCents <= 0) throw new IllegalArgumentException("Valor precisa ser maior que zero.");
    }
}

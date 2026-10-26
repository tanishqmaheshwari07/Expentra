package com.example.expensetracker.repository;

import com.example.expensetracker.dto.ConsumedOAuthCode;

import java.util.Optional;

public interface OAuthExchangeCodeRepositoryCustom {
    Optional<ConsumedOAuthCode> consumeCodeAtomically(String codeHash);
}

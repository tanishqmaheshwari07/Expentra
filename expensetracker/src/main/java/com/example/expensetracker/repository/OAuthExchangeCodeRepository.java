package com.example.expensetracker.repository;

import com.example.expensetracker.entity.OAuthExchangeCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface OAuthExchangeCodeRepository extends JpaRepository<OAuthExchangeCode, String>, OAuthExchangeCodeRepositoryCustom {

    @Modifying
    @Query("DELETE FROM OAuthExchangeCode c WHERE c.expiresAt <= :now")
    int deleteExpiredCodes(@Param("now") Instant now);
}

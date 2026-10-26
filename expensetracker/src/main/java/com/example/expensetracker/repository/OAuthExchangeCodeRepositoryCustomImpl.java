package com.example.expensetracker.repository;

import com.example.expensetracker.dto.ConsumedOAuthCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class OAuthExchangeCodeRepositoryCustomImpl implements OAuthExchangeCodeRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;
    private volatile Boolean isPostgres;

    @Override
    public Optional<ConsumedOAuthCode> consumeCodeAtomically(String codeHash) {
        if (isPostgreSql()) {
            // PostgreSQL native atomic query: DELETE ... RETURNING
            String sql = "DELETE FROM oauth_exchange_codes WHERE code_hash = ? AND expires_at > CURRENT_TIMESTAMP RETURNING user_id, binder_hash";
            List<ConsumedOAuthCode> results = jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> new ConsumedOAuthCode(rs.getLong("user_id"), rs.getString("binder_hash")),
                    codeHash
            );
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } else {
            // Fallback for H2 in-memory test database (H2 doesn't support RETURNING on DELETE)
            List<ConsumedOAuthCode> results = jdbcTemplate.query(
                    "SELECT user_id, binder_hash FROM oauth_exchange_codes WHERE code_hash = ? AND expires_at > CURRENT_TIMESTAMP FOR UPDATE",
                    (rs, rowNum) -> new ConsumedOAuthCode(rs.getLong("user_id"), rs.getString("binder_hash")),
                    codeHash
            );
            if (results.isEmpty()) {
                return Optional.empty();
            }
            int deleted = jdbcTemplate.update("DELETE FROM oauth_exchange_codes WHERE code_hash = ?", codeHash);
            if (deleted == 0) {
                return Optional.empty();
            }
            return Optional.of(results.get(0));
        }
    }

    private boolean isPostgreSql() {
        if (isPostgres == null) {
            synchronized (this) {
                if (isPostgres == null) {
                    try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
                        String name = conn.getMetaData().getDatabaseProductName();
                        isPostgres = name != null && name.toLowerCase().contains("postgres");
                    } catch (SQLException e) {
                        log.warn("Could not determine database product name, assuming PostgreSQL", e);
                        isPostgres = true;
                    }
                }
            }
        }
        return isPostgres;
    }
}

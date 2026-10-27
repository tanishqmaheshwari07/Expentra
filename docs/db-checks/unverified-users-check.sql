-- Run manually against your own database. Read-only. Review before any UPDATE.
-- =============================================================================
-- Pre-cleanup Safety Audit: Unverified LOCAL User Accounts & Expense Ownership
-- =============================================================================

-- 1. List unverified LOCAL users with created_at and a count of ALL their expenses
--    (includes soft-deleted expenses, i.e. does NOT filter on is_deleted).
SELECT 
    u.id AS user_id,
    u.email,
    u.name,
    u.created_at,
    COUNT(e.id) AS total_expense_count
FROM users u
LEFT JOIN expenses e ON e.user_id = u.id
WHERE u.auth_provider = 'LOCAL'
  AND u.email_verified = false
GROUP BY u.id, u.email, u.name, u.created_at
ORDER BY u.created_at ASC;

-- 2. Count of users that the scheduled cleanup job WOULD delete
--    (unverified, LOCAL, older than 7 days, zero expenses).
SELECT COUNT(*) AS cleanup_eligible_users_count
FROM users u
WHERE u.auth_provider = 'LOCAL'
  AND u.email_verified = false
  AND u.created_at < (NOW() - INTERVAL '7 days')
  AND NOT EXISTS (
      SELECT 1 FROM expenses e WHERE e.user_id = u.id
  );

-- 3. Count of unverified LOCAL users that have expenses
--    (these accounts MUST be reviewed and manually backfilled/verified by ID before enabling cleanup).
SELECT COUNT(*) AS unverified_local_users_with_expenses_count
FROM users u
WHERE u.auth_provider = 'LOCAL'
  AND u.email_verified = false
  AND EXISTS (
      SELECT 1 FROM expenses e WHERE e.user_id = u.id
  );

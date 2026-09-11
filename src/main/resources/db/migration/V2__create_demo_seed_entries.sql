CREATE TABLE demo_seed_entries (
    seed_key VARCHAR(100) PRIMARY KEY,
    expense_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- The marker deliberately has no foreign key to expense_requests. It must
-- survive a user renaming or deleting a seeded request so the initializer
-- does not recreate it on the next startup.
INSERT INTO demo_seed_entries (seed_key, expense_id)
SELECT 'expense-' || lpad(seed.seed_number::text, 2, '0'),
       min(request.id)
FROM generate_series(1, 45) AS seed(seed_number)
JOIN expense_requests request
  ON request.title = 'デモ申請-' || lpad(seed.seed_number::text, 2, '0')
GROUP BY seed.seed_number
ON CONFLICT (seed_key) DO NOTHING;

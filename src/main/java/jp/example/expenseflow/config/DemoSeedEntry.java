package jp.example.expenseflow.config;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Durable identity for one demo seed, independent of mutable expense data. */
@Entity
@Table(name = "demo_seed_entries")
public class DemoSeedEntry {

    @Id
    @Column(name = "seed_key", nullable = false, length = 100)
    private String seedKey;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DemoSeedEntry() {
    }

    public DemoSeedEntry(String seedKey, Long expenseId, Instant createdAt) {
        this.seedKey = seedKey;
        this.expenseId = expenseId;
        this.createdAt = createdAt;
    }

    public String getSeedKey() {
        return seedKey;
    }

    public Long getExpenseId() {
        return expenseId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

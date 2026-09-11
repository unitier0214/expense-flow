package jp.example.expenseflow.config;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoSeedEntryRepository extends JpaRepository<DemoSeedEntry, String> {
}

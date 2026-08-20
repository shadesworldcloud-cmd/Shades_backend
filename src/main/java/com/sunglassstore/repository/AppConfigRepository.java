package com.sunglassstore.repository;

import com.sunglassstore.entity.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Access to the CONFIG key/value table.
 *
 * The table and its entity already existed in this schema but nothing read or wrote them — the
 * storefront settings below are its first use. That is why these two features needed no migration:
 * CONFIG_SHORT_CODE is already UNIQUE, so a settings key cannot be duplicated by the database.
 */
public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    Optional<AppConfig> findByConfigShortCode(String configShortCode);
}

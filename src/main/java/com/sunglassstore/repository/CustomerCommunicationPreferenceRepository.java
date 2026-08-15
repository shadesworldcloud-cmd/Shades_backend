package com.sunglassstore.repository;

import com.sunglassstore.entity.CustomerCommunicationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerCommunicationPreferenceRepository extends JpaRepository<CustomerCommunicationPreference, Long> {
    Optional<CustomerCommunicationPreference> findByUserEmailIgnoreCase(String email);
}

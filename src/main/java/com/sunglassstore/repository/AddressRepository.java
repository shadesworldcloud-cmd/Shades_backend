package com.sunglassstore.repository;

import com.sunglassstore.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);

    Optional<Address> findByAddressIdAndUserUserId(Long addressId, Long userId);

    boolean existsByUserUserId(Long userId);

    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.user.userId = :userId AND a.addressId <> :addressId")
    void clearDefaultForUser(Long userId, Long addressId);
}

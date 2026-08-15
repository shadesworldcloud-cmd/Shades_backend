package com.sunglassstore.repository;

import com.sunglassstore.entity.Cart;
import com.sunglassstore.entity.enums.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserUserIdAndCartStatus(Long userId, CartStatus cartStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT DISTINCT c FROM Cart c LEFT JOIN FETCH c.items WHERE c.user.userId = :userId AND c.cartStatus = :cartStatus")
    Optional<Cart> findByUserUserIdAndCartStatusForUpdate(Long userId, CartStatus cartStatus);
}

package com.sunglassstore.repository;

import com.sunglassstore.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCouponCodeIgnoreCase(String couponCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE LOWER(c.couponCode) = LOWER(:couponCode)")
    Optional<Coupon> findByCouponCodeIgnoreCaseForUpdate(String couponCode);

    boolean existsByCouponCodeIgnoreCase(String couponCode);

    boolean existsByCouponCodeIgnoreCaseAndCouponIdNot(String couponCode, Long couponId);

    Page<Coupon> findAllByOrderByCouponIdDesc(Pageable pageable);
}

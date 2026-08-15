package com.sunglassstore.repository;

import com.sunglassstore.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    @Query("SELECT COUNT(cu) FROM CouponUsage cu WHERE cu.coupon.couponId = :couponId " +
            "AND cu.order.orderStatus <> com.sunglassstore.entity.enums.OrderStatus.CANCELLED")
    long countByCouponCouponId(@Param("couponId") Long couponId);

    @Query("SELECT COUNT(cu) FROM CouponUsage cu WHERE cu.coupon.couponId = :couponId " +
            "AND cu.user.userId = :userId " +
            "AND cu.order.orderStatus <> com.sunglassstore.entity.enums.OrderStatus.CANCELLED")
    long countByCouponCouponIdAndUserUserId(@Param("couponId") Long couponId,
                                            @Param("userId") Long userId);
}

package com.sunglassstore.repository;

import com.sunglassstore.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    List<OrderStatusHistory> findByOrderOrderIdOrderByChangedAtAsc(Long orderId);
}

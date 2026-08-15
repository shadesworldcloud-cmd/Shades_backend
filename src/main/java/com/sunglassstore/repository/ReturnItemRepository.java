package com.sunglassstore.repository;

import com.sunglassstore.entity.ReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {

    @Query("SELECT COALESCE(SUM(ri.quantity), 0) FROM ReturnItem ri " +
           "WHERE ri.orderItem.orderItemId = :orderItemId " +
           "AND ri.returnRequest.returnStatus NOT IN " +
           "(com.sunglassstore.entity.enums.ReturnStatus.REJECTED, " +
           "com.sunglassstore.entity.enums.ReturnStatus.CANCELLED)")
    Integer sumReturnedQuantityByOrderItemId(Long orderItemId);

    @Query("SELECT COALESCE(SUM(ri.quantity), 0) FROM ReturnItem ri " +
           "WHERE ri.orderItem.orderItemId = :orderItemId " +
           "AND ri.returnRequest.returnStatus IN " +
           "(com.sunglassstore.entity.enums.ReturnStatus.RECEIVED, " +
           "com.sunglassstore.entity.enums.ReturnStatus.COMPLETED)")
    Integer sumPhysicallyReturnedQuantityByOrderItemId(Long orderItemId);
}

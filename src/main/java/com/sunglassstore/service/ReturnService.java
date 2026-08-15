package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateReturnRequest;
import com.sunglassstore.dto.response.ReturnResponse;
import com.sunglassstore.entity.enums.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Map;

public interface ReturnService {
    ReturnResponse createReturn(Long userId, CreateReturnRequest request);
    Page<ReturnResponse> getUserReturns(Long userId, Pageable pageable);
    ReturnResponse getReturnById(Long userId, Long returnId);
    Page<ReturnResponse> getAllReturns(Pageable pageable);
    ReturnResponse updateReturnStatus(Long returnId, ReturnStatus status, String adminComments, Map<Long, String> itemConditions);
    ReturnResponse cancelReturn(Long userId, Long returnId);
}

package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateCommunicationPreferencesRequest {
    @NotNull private Boolean emailOrderUpdates;
    @NotNull private Boolean emailShipmentUpdates;
    @NotNull private Boolean emailReturnRefundUpdates;
    @NotNull private Boolean inAppOrderUpdates;
    @NotNull private Boolean inAppShipmentUpdates;
    @NotNull private Boolean inAppReturnRefundUpdates;
    @NotNull private Boolean inAppReviewUpdates;
}

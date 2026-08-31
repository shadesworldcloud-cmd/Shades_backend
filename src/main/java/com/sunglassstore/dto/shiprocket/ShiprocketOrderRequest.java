package com.sunglassstore.dto.shiprocket;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Maps to Shiprocket's POST /orders/create/adhoc request body.
 */
public record ShiprocketOrderRequest(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("order_date") String orderDate,
        @JsonProperty("pickup_location") String pickupLocation,
        @JsonProperty("channel_id") String channelId,
        @JsonProperty("comment") String comment,

        // Billing
        @JsonProperty("billing_customer_name") String billingCustomerName,
        @JsonProperty("billing_last_name") String billingLastName,
        @JsonProperty("billing_address") String billingAddress,
        @JsonProperty("billing_address_2") String billingAddress2,
        @JsonProperty("billing_city") String billingCity,
        @JsonProperty("billing_pincode") String billingPincode,
        @JsonProperty("billing_state") String billingState,
        @JsonProperty("billing_country") String billingCountry,
        @JsonProperty("billing_email") String billingEmail,
        @JsonProperty("billing_phone") String billingPhone,

        // Shipping
        @JsonProperty("shipping_is_billing") boolean shippingIsBilling,
        @JsonProperty("shipping_customer_name") String shippingCustomerName,
        @JsonProperty("shipping_last_name") String shippingLastName,
        @JsonProperty("shipping_address") String shippingAddress,
        @JsonProperty("shipping_address_2") String shippingAddress2,
        @JsonProperty("shipping_city") String shippingCity,
        @JsonProperty("shipping_pincode") String shippingPincode,
        @JsonProperty("shipping_state") String shippingState,
        @JsonProperty("shipping_country") String shippingCountry,
        @JsonProperty("shipping_email") String shippingEmail,
        @JsonProperty("shipping_phone") String shippingPhone,

        // Order details
        @JsonProperty("order_items") List<Item> orderItems,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("sub_total") BigDecimal subTotal,

        // Dimensions & weight (required for rate calculation)
        @JsonProperty("length") BigDecimal length,
        @JsonProperty("breadth") BigDecimal breadth,
        @JsonProperty("height") BigDecimal height,
        @JsonProperty("weight") BigDecimal weight
) {
    public record Item(
            @JsonProperty("name") String name,
            @JsonProperty("sku") String sku,
            @JsonProperty("units") int units,
            @JsonProperty("selling_price") BigDecimal sellingPrice,
            @JsonProperty("discount") BigDecimal discount,
            @JsonProperty("tax") BigDecimal tax,
            @JsonProperty("hsn") String hsn
    ) {}
}

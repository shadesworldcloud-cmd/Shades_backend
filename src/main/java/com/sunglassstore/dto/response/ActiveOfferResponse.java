package com.sunglassstore.dto.response;

import com.sunglassstore.entity.AutomaticOffer;
import com.sunglassstore.offer.OfferMessages;

import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the storefront needs to render the banner and label eligible items.
 *
 * No dates, no version, no audit fields, no scope ids the customer has no use for beyond knowing
 * which products qualify. `active` is a field rather than the response being absent so the caller
 * can distinguish "no offer" from "request failed" and avoid a layout shift either way.
 */
public record ActiveOfferResponse(boolean active,
                                  Long automaticOfferId,
                                  String offerName,
                                  String bannerMessage,
                                  String termsMessage,
                                  Integer requiredQuantity,
                                  BigDecimal discountPerGroup,
                                  BigDecimal minimumOrderSubtotal,
                                  String scopeType,
                                  Set<Long> productIds,
                                  Set<Long> categoryIds) {

    public static final ActiveOfferResponse NONE =
            new ActiveOfferResponse(false, null, null, null, null, null, null, null, null, Set.of(), Set.of());

    public static ActiveOfferResponse fromEntity(AutomaticOffer offer) {
        return new ActiveOfferResponse(true,
                offer.getAutomaticOfferId(),
                offer.getOfferName(),
                OfferMessages.banner(offer),
                OfferMessages.terms(offer),
                offer.getRequiredQuantity(),
                offer.getDiscountPerGroup(),
                offer.getMinimumOrderSubtotal(),
                offer.getScopeType().name(),
                new TreeSet<>(offer.getProductIds()),
                new TreeSet<>(offer.getCategoryIds()));
    }
}

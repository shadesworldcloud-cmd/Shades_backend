package com.sunglassstore.dto.response;

import com.sunglassstore.entity.AutomaticOffer;
import com.sunglassstore.offer.OfferMessages;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeSet;

/**
 * Administrator view of an automatic offer.
 *
 * `state` is derived rather than stored: active/inactive is a stored flag but scheduled and expired
 * are facts about the clock, and a stored copy of them would need a scheduler to stay true.
 * `effectiveBannerMessage` is what customers would actually see, so the administrator can tell at a
 * glance whether their custom wording or the generated default is in force.
 */
public record AutomaticOfferResponse(Long automaticOfferId,
                                     String offerName,
                                     String bannerMessage,
                                     String effectiveBannerMessage,
                                     String termsMessage,
                                     Integer requiredQuantity,
                                     BigDecimal discountPerGroup,
                                     BigDecimal minimumOrderSubtotal,
                                     String scopeType,
                                     Set<Long> productIds,
                                     Set<Long> categoryIds,
                                     Boolean isActive,
                                     String state,
                                     LocalDateTime startsAt,
                                     LocalDateTime endsAt,
                                     Integer priority,
                                     Long version,
                                     LocalDateTime archivedAt,
                                     LocalDateTime createdAt,
                                     LocalDateTime updatedAt) {

    public static AutomaticOfferResponse fromEntity(AutomaticOffer offer, LocalDateTime now) {
        return new AutomaticOfferResponse(
                offer.getAutomaticOfferId(),
                offer.getOfferName(),
                offer.getBannerMessage(),
                OfferMessages.banner(offer),
                OfferMessages.terms(offer),
                offer.getRequiredQuantity(),
                offer.getDiscountPerGroup(),
                offer.getMinimumOrderSubtotal(),
                offer.getScopeType().name(),
                // Sorted so a response body is stable between calls and diffable in a test.
                new TreeSet<>(offer.getProductIds()),
                new TreeSet<>(offer.getCategoryIds()),
                offer.getIsActive(),
                state(offer, now),
                offer.getStartsAt(),
                offer.getEndsAt(),
                offer.getPriority(),
                offer.getVersion(),
                offer.getArchivedAt(),
                offer.getCreatedAt(),
                offer.getUpdatedAt());
    }

    private static String state(AutomaticOffer offer, LocalDateTime now) {
        if (offer.getArchivedAt() != null) return "ARCHIVED";
        if (!Boolean.TRUE.equals(offer.getIsActive())) return "INACTIVE";
        if (offer.getStartsAt().isAfter(now)) return "SCHEDULED";
        if (!offer.getEndsAt().isAfter(now)) return "EXPIRED";
        return "ACTIVE";
    }
}

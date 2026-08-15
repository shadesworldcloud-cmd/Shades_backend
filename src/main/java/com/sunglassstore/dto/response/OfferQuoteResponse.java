package com.sunglassstore.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * The server's priced view of a cart: the single number checkout is allowed to charge, plus enough
 * detail for the bag and checkout pages to explain it.
 *
 * The frontend renders these values rather than computing its own. It may still show an estimate
 * while this is in flight, but the figures the customer confirms and the figure sent back as
 * expectedTotalAmount both come from here, priced from current database state.
 *
 * `unresolvedVariantIds` names lines the catalogue could not price — inactive, deleted or
 * out-of-stock. They are excluded from every amount below, which is what keeps the quote consistent
 * with what checkout can actually fulfil.
 */
public record OfferQuoteResponse(BigDecimal subtotal,
                                 int itemQuantity,
                                 BigDecimal discount,
                                 BigDecimal taxableAmount,
                                 BigDecimal taxAmount,
                                 BigDecimal shippingAmount,
                                 BigDecimal totalAmount,
                                 String appliedPromotion,
                                 String appliedPromotionLabel,
                                 String suppressedPromotionLabel,
                                 String suppressedPromotionReason,
                                 AutomaticOfferQuote automaticOffer,
                                 List<Long> unresolvedVariantIds,
                                 /**
                                  * Why no discount was applied, in plain language, or null when one
                                  * was. Added because "the offer is not applying" is unanswerable
                                  * from a quote that simply omits it — the cart cannot tell a
                                  * scheduled offer from an expired one from an ineligible bag, and
                                  * neither could anyone debugging it. Deliberately free of anything
                                  * sensitive: offer name, dates and quantities only, all of which
                                  * the customer is already shown.
                                  */
                                 String diagnostic) {

    /**
     * @param eligibleQuantity units that counted toward groups
     * @param unitsToNextGroup how many more eligible units earn another group; drives the
     *                         "add 1 more" hint
     * @param progressMessage  that hint, generated server-side so the wording cannot disagree with
     *                         the numbers it describes
     */
    public record AutomaticOfferQuote(Long automaticOfferId,
                                      String offerName,
                                      String termsMessage,
                                      Integer requiredQuantity,
                                      BigDecimal discountPerGroup,
                                      int eligibleQuantity,
                                      BigDecimal eligibleSubtotal,
                                      int completeGroups,
                                      BigDecimal discount,
                                      int unitsToNextGroup,
                                      String progressMessage,
                                      Set<Long> eligibleVariantIds) {
    }
}

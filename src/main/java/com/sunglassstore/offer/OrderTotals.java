package com.sunglassstore.offer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The order money rules — tax, shipping and the final total — in one place.
 *
 * These lived as private constants and inline arithmetic inside OrderServiceImpl, which was fine
 * while order creation was the only thing that computed a total. It is not any more: the cart quote
 * has to produce the same number order creation will, because checkout sends that number back as
 * expectedTotalAmount and order creation refuses the order if it differs by a paisa. Two copies of
 * this arithmetic would make that check fire on correct carts.
 *
 * Rules preserved exactly as they were:
 *   taxable  = subtotal - discount
 *   tax      = 18% of taxable, half-up to the paisa
 *   shipping = free at or above ₹500 of *subtotal*, otherwise ₹49
 *   total    = taxable + tax + shipping
 *
 * Shipping keys off the pre-discount subtotal deliberately — that is the existing behaviour, and
 * changing it would move the free-shipping line for every order that uses a discount.
 */
public record OrderTotals(BigDecimal subtotal,
                          BigDecimal discount,
                          BigDecimal taxable,
                          BigDecimal tax,
                          BigDecimal shipping,
                          BigDecimal total) {

    public static final BigDecimal TAX_RATE = new BigDecimal("18.00");
    public static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("500.00");
    public static final BigDecimal STANDARD_SHIPPING = new BigDecimal("49.00");

    public static OrderTotals of(BigDecimal subtotal, BigDecimal discount) {
        BigDecimal safeSubtotal = scale(subtotal == null ? BigDecimal.ZERO : subtotal);
        BigDecimal safeDiscount = scale(discount == null ? BigDecimal.ZERO : discount);
        // A discount can never exceed the merchandise it discounts, so the total can never go
        // negative. The pricing rule already caps it; clamping here as well means a caller that
        // hands over a bad figure gets a zero-taxable order rather than a negative charge.
        if (safeDiscount.compareTo(safeSubtotal) > 0) {
            safeDiscount = safeSubtotal;
        }
        BigDecimal taxable = safeSubtotal.subtract(safeDiscount);
        BigDecimal tax = taxable.multiply(TAX_RATE).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal shipping = safeSubtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO.setScale(2)
                : (safeSubtotal.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO.setScale(2) : STANDARD_SHIPPING);
        return new OrderTotals(safeSubtotal, safeDiscount, taxable, tax, shipping,
                taxable.add(tax).add(shipping));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

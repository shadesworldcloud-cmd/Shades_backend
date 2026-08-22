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
 * Prices are TAX-INCLUSIVE. The price an administrator types against a variant is what the
 * customer pays for that item; GST is extracted from it for disclosure, never added on top. Before
 * this change tax was additive (₹5,000 of merchandise was charged at ₹5,900), which meant the
 * administrator's price was not the price and every shelf figure was 18% short of the checkout one.
 *
 *   gross    = subtotal - discount        // what the customer pays for merchandise, tax included
 *   taxable  = gross / 1.18, half-up to the paisa
 *   tax      = gross - taxable            // the REMAINDER, not 18% of taxable — see below
 *   shipping = free at or above ₹500 of *subtotal*, otherwise ₹49
 *   total    = taxable + tax + shipping   // identically gross + shipping
 *
 * Tax is the remainder rather than `taxable * 18%` because the two disagree by a paisa on 685 of
 * the 4,501 whole-rupee prices between ₹500 and ₹5,000. At ₹513 the net is ₹434.75; by subtraction
 * the tax is ₹78.25 and the invoice foots to ₹513.00, while by multiplication it is ₹78.26 and the
 * invoice claims ₹513.01 — a rupee document disagreeing with the amount actually charged. Deriving
 * by subtraction makes taxable + tax == gross true by construction at every value.
 *
 * Shipping keys off the pre-discount subtotal deliberately — that is the existing behaviour, and
 * changing it would move the free-shipping line for every order that uses a discount. Shipping sits
 * outside the tax extraction for the same reason: it was untaxed before and still is.
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
    /** The factor a net amount is multiplied by to reach the tax-inclusive shelf price: 1.1800. */
    private static final BigDecimal TAX_INCLUSIVE_FACTOR = BigDecimal.ONE.add(TAX_RATE.movePointLeft(2));

    public static OrderTotals of(BigDecimal subtotal, BigDecimal discount) {
        BigDecimal safeSubtotal = scale(subtotal == null ? BigDecimal.ZERO : subtotal);
        BigDecimal safeDiscount = scale(discount == null ? BigDecimal.ZERO : discount);
        // A discount can never exceed the merchandise it discounts, so the total can never go
        // negative. The pricing rule already caps it; clamping here as well means a caller that
        // hands over a bad figure gets a zero-taxable order rather than a negative charge.
        if (safeDiscount.compareTo(safeSubtotal) > 0) {
            safeDiscount = safeSubtotal;
        }
        // Tax-inclusive: this is the money the customer hands over for merchandise, GST already in it.
        BigDecimal gross = safeSubtotal.subtract(safeDiscount);
        BigDecimal taxable = gross.divide(TAX_INCLUSIVE_FACTOR, 2, RoundingMode.HALF_UP);
        BigDecimal tax = gross.subtract(taxable);
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

package com.sunglassstore.service.impl;

import com.sunglassstore.dto.response.AdminOrderResponse;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.service.InvoiceService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
public class InvoiceServiceImpl implements InvoiceService {
    private static final Set<String> INVOICE_PAYMENT_STATUSES = Set.of("PAID", "PARTIALLY_REFUNDED", "REFUNDED");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDColor INK = new PDColor(new float[]{35 / 255f, 48 / 255f, 40 / 255f}, PDDeviceRGB.INSTANCE);
    private static final PDColor MUTED = new PDColor(new float[]{105 / 255f, 105 / 255f, 98 / 255f}, PDDeviceRGB.INSTANCE);
    private static final PDColor SAND = new PDColor(new float[]{232 / 255f, 224 / 255f, 210 / 255f}, PDDeviceRGB.INSTANCE);

    private final String sellerName;
    private final String sellerEmail;
    private final String sellerAddress;
    private final String sellerContact;
    private final String sellerGstin;
    private final String sellerPan;
    private final String sellerState;
    private final String sellerWebsite;
    private final String productHsn;
    private final BigDecimal gstRate;

    public InvoiceServiceImpl(
            @Value("${invoice.seller.name:Shades World Barcelona}") String sellerName,
            @Value("${invoice.seller.email:shadesworldindia11@gmail.com}") String sellerEmail,
            @Value("${invoice.seller.address:Shop 1, Plot 366, Lane 2, Opp Axis Bank, Raja Park, Jaipur, Rajasthan - 302004}") String sellerAddress,
            @Value("${invoice.seller.contact:8233511042}") String sellerContact,
            @Value("${invoice.seller.gstin:08CFEPJ4650Q1ZX}") String sellerGstin,
            @Value("${invoice.seller.pan:CFEPJ4650Q}") String sellerPan,
            @Value("${invoice.seller.state:Rajasthan - 08}") String sellerState,
            @Value("${invoice.seller.website:www.shadesworld.online}") String sellerWebsite,
            @Value("${invoice.product.hsn:90041000}") String productHsn,
            @Value("${invoice.gst-rate:18.00}") BigDecimal gstRate) {
        this.sellerName = sellerName;
        this.sellerEmail = sellerEmail;
        this.sellerAddress = sellerAddress;
        this.sellerContact = sellerContact;
        this.sellerGstin = sellerGstin;
        this.sellerPan = sellerPan;
        this.sellerState = sellerState;
        this.sellerWebsite = sellerWebsite;
        this.productHsn = productHsn;
        this.gstRate = gstRate;
    }

    @Override
    public byte[] generate(AdminOrderResponse order) {
        if (order == null) throw new BadRequestException("Order is required");
        boolean paid = order.payments() != null && order.payments().stream()
                .anyMatch(payment -> INVOICE_PAYMENT_STATUSES.contains(payment.status()));
        if (!paid) throw new BadRequestException("Invoice is available after payment is recorded");

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            InvoiceCanvas canvas = new InvoiceCanvas(document, order);
            canvas.header();
            canvas.parties();
            canvas.items(order.items());
            canvas.totals();
            canvas.payment();
            canvas.footer();
            canvas.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate invoice", exception);
        }
    }

    private final class InvoiceCanvas {
        private final PDDocument document;
        private final AdminOrderResponse order;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        private InvoiceCanvas(PDDocument document, AdminOrderResponse order) throws IOException {
            this.document = document;
            this.order = order;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = 790;
            pageNumber++;
        }

        private void header() throws IOException {
            stream.setNonStrokingColor(SAND);
            stream.addRect(36, 728, 523, 88);
            stream.fill();
            drawLogo(48, 750, 92, 36);
            text("TAX INVOICE", 247, 799, BOLD, 11, INK);
            text(sellerName.toUpperCase(), 205, 778, BOLD, 18, INK);
            text("Premium eyewear", 254, 762, BOLD, 7, MUTED);
            text("Invoice no.", 390, 795, REGULAR, 7, MUTED);
            right("SW-" + order.orderId(), 547, 795, BOLD, 9, INK);
            text("Invoice date", 390, 779, REGULAR, 7, MUTED);
            right(DATE.format(order.purchasedAt()), 547, 779, BOLD, 7, INK);
            text("Order status", 390, 763, REGULAR, 7, MUTED);
            right(order.orderStatus(), 547, 763, BOLD, 7, INK);
            y = 710;
        }

        private void parties() throws IOException {
            label("SELLER", 46, y);
            text(sellerName, 46, y - 17, BOLD, 9, INK);
            float sellerY = wrapped(sellerAddress, 46, y - 31, 238, REGULAR, 7, MUTED, 9);
            text("GSTIN: " + sellerGstin + "   PAN: " + sellerPan, 46, sellerY, BOLD, 7, INK);
            text("State: " + sellerState, 46, sellerY - 11, REGULAR, 7, MUTED);
            text("Contact: " + sellerContact + "   Email: " + sellerEmail, 46, sellerY - 22, REGULAR, 7, MUTED);
            text("Website: " + sellerWebsite, 46, sellerY - 33, REGULAR, 7, MUTED);

            label("RECEIVER (BILLED TO) / CONSIGNEE (SHIPPED TO)", 316, y);
            AdminOrderResponse.ShippingAddress address = order.shippingAddress();
            text(address.name(), 316, y - 17, BOLD, 9, INK);
            float addressY = wrapped(address.line1() + optional(", " + address.line2(), address.line2()),
                    316, y - 31, 230, REGULAR, 7, MUTED, 9);
            text(address.city() + ", " + address.state() + " - " + address.pincode(), 316, addressY, REGULAR, 7, MUTED);
            text(address.country(), 316, addressY - 11, REGULAR, 7, MUTED);
            text("Mobile: " + safe(address.phone()), 316, addressY - 24, REGULAR, 7, MUTED);
            text("Email: " + safe(order.customer().email()), 316, addressY - 35, REGULAR, 7, MUTED);
            y -= 116;
            line(46, y + 5, 549, y + 5);
        }

        private void items(List<AdminOrderResponse.Item> items) throws IOException {
            tableHeader();
            for (AdminOrderResponse.Item item : items) {
                if (y < 105) {
                    footer();
                    newPage();
                    text("INVOICE SW-" + order.orderId() + " - CONTINUED", 46, 800, BOLD, 11, INK);
                    y = 770;
                    tableHeader();
                }
                float rowTop = y;
                text(clip(item.productName(), 28), 46, rowTop - 17, BOLD, 8, INK);
                text("SKU " + clip(item.sku(), 22), 46, rowTop - 30, REGULAR, 6.5f, MUTED);
                text(productHsn, 275, rowTop - 21, REGULAR, 7, INK);
                right(gstRate.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%", 369, rowTop - 21, REGULAR, 7, INK);
                right(String.valueOf(item.quantity()), 400, rowTop - 21, REGULAR, 7, INK);
                right(money(item.unitPrice()), 453, rowTop - 21, REGULAR, 7, INK);
                right(money(item.discountAmount()), 500, rowTop - 21, REGULAR, 7, INK);
                right(money(item.lineTotal()), 549, rowTop - 21, BOLD, 7, INK);
                line(46, rowTop - 43, 549, rowTop - 43);
                y -= 44;
            }
            y -= 12;
        }

        private void tableHeader() throws IOException {
            stream.setNonStrokingColor(INK);
            stream.addRect(46, y - 28, 503, 28);
            stream.fill();
            text("ITEM", 56, y - 18, BOLD, 8, white());
            text("HSN/SAC", 275, y - 18, BOLD, 6.5f, white());
            right("GST", 369, y - 18, BOLD, 6.5f, white());
            right("QTY", 400, y - 18, BOLD, 6.5f, white());
            right("RATE", 453, y - 18, BOLD, 6.5f, white());
            right("DISC", 500, y - 18, BOLD, 6.5f, white());
            right("AMOUNT", 549, y - 18, BOLD, 6.5f, white());
            y -= 28;
        }

        private void totals() throws IOException {
            ensure(170);
            // Derived from the total rather than recomputed from the subtotal, because this document
            // has to be correct for orders priced under both regimes. Rows written before prices
            // became tax-inclusive have total = (subtotal - discount) + tax + shipping, so their
            // taxable IS subtotal - discount; rows written after have total = (subtotal - discount)
            // + shipping with the tax already inside the merchandise, so their taxable is that
            // figure MINUS tax. Subtracting shipping and tax from the total yields the right answer
            // for both, and guarantees the GST summary foots to the net amount by construction —
            // which the old formula would not do for a tax-inclusive order, overstating the taxable
            // base by the whole tax and making the column add up to more than was charged.
            BigDecimal taxable = nvl(order.totalAmount())
                    .subtract(nvl(order.shippingAmount()))
                    .subtract(nvl(order.taxAmount()));
            boolean intraState = safe(order.shippingAddress().state()).toLowerCase().contains("rajasthan");
            BigDecimal halfTax = nvl(order.taxAmount()).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            float summaryY = y;
            label("GST SUMMARY", 46, summaryY);
            text("HSN/SAC", 46, summaryY - 17, BOLD, 7, MUTED);
            text("TAXABLE", 115, summaryY - 17, BOLD, 7, MUTED);
            text(intraState ? "CGST" : "IGST", 180, summaryY - 17, BOLD, 7, MUTED);
            if (intraState) text("SGST", 235, summaryY - 17, BOLD, 7, MUTED);
            text(productHsn, 46, summaryY - 34, REGULAR, 7, INK);
            right(money(taxable), 166, summaryY - 34, REGULAR, 7, INK);
            right(money(intraState ? halfTax : order.taxAmount()), 225, summaryY - 34, REGULAR, 7, INK);
            if (intraState) right(money(halfTax), 280, summaryY - 34, REGULAR, 7, INK);
            text("GST rate " + gstRate.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%", 46, summaryY - 51, REGULAR, 7, MUTED);

            float x = 355;
            totalRow("Amount before discount", order.subtotalAmount(), x, false);
            // The discount line is named after whatever actually granted it, taken from the order's
            // own snapshot. A generic "Discount" on a document the customer keeps for months would
            // leave no record of which offer applied — and the offer itself may since have changed.
            totalRow(discountLabel(order), order.discountAmount().negate(), x, false);
            totalRow("Taxable amount", taxable, x, false);
            if (intraState) {
                totalRow("CGST", halfTax, x, false);
                totalRow("SGST", halfTax, x, false);
            } else totalRow("IGST", order.taxAmount(), x, false);
            totalRow("Freight / shipping", order.shippingAmount(), x, false);
            line(x, y - 4, 549, y - 4);
            y -= 22;
            totalRow("NET AMOUNT", order.totalAmount(), x, true);
            text("Amount in words: " + amountInWords(order.totalAmount()), 46, y + 6, BOLD, 7, INK);
            y -= 10;
        }

        /**
         * "Discount (2 for ₹500 Weekend — 3 groups)" rather than "Discount".
         *
         * Truncated so a long administrator-chosen offer name cannot run past the totals column and
         * over the amount beside it.
         */
        private String discountLabel(com.sunglassstore.dto.response.AdminOrderResponse order) {
            var offer = order.appliedOffer();
            if (offer != null) {
                String name = offer.offerName() == null ? "Automatic offer" : offer.offerName();
                if (name.length() > 28) name = name.substring(0, 27).trim() + "…";
                Integer groups = offer.completeGroups();
                return "Discount (" + name + (groups == null ? "" : " — " + groups
                        + (groups == 1 ? " group" : " groups")) + ")";
            }
            if (order.couponCode() != null) {
                return "Discount (coupon " + order.couponCode() + ")";
            }
            return "Discount";
        }

        private void totalRow(String name, BigDecimal value, float x, boolean bold) throws IOException {
            text(name, x, y, bold ? BOLD : REGULAR, bold ? 11 : 9, bold ? INK : MUTED);
            right(amount(value), 549, y, bold ? BOLD : REGULAR, bold ? 12 : 9, INK);
            y -= bold ? 24 : 18;
        }

        private void payment() throws IOException {
            ensure(135);
            AdminOrderResponse.PaymentInfo payment = order.payments().stream()
                    .filter(item -> INVOICE_PAYMENT_STATUSES.contains(item.status())).findFirst().orElseThrow();
            line(46, y + 8, 549, y + 8);
            label("PAYMENT DETAILS", 46, y);
            text("Mode: " + payment.method(), 46, y - 17, BOLD, 8, INK);
            text("Status: " + payment.status(), 150, y - 17, BOLD, 8, INK);
            text("Amount: " + amount(payment.amount()), 250, y - 17, BOLD, 8, INK);
            text("Reference: " + (payment.reference() == null ? "Not supplied" : payment.reference()), 46, y - 33, REGULAR, 7, MUTED);
            label("TERMS AND CONDITIONS", 46, y - 54);
            text("- Exchange is allowed within 3 days, subject to product inspection and condition.", 46, y - 70, REGULAR, 6.5f, MUTED);
            text("- Sunglasses include a 6-month warranty against manufacturing defects.", 46, y - 81, REGULAR, 6.5f, MUTED);
            text("- Physical damage, accidental breakage, misuse and mishandling are not covered.", 46, y - 92, REGULAR, 6.5f, MUTED);
            text("For " + sellerName, 420, y - 70, BOLD, 7, INK);
            text("Authorised Signatory", 438, y - 97, BOLD, 7, INK);
            y -= 118;
        }

        private void footer() throws IOException {
            text("Thank you for choosing Shades World.", 46, 44, REGULAR, 8, MUTED);
            text("GSTIN " + sellerGstin + " | " + sellerWebsite, 205, 44, REGULAR, 7, MUTED);
            right("Page " + pageNumber, 549, 44, REGULAR, 8, MUTED);
        }

        private void ensure(float height) throws IOException {
            if (y - height < 70) {
                footer();
                newPage();
                y = 780;
            }
        }

        private void drawLogo(float x, float atY, float width, float height) throws IOException {
            try (InputStream input = InvoiceServiceImpl.class.getResourceAsStream("/invoice/shades-world-logo.jpg")) {
                if (input == null) {
                    text("SHADES WORLD", x, atY + 10, BOLD, 11, INK);
                    return;
                }
                PDImageXObject logo = PDImageXObject.createFromByteArray(document, input.readAllBytes(), "shades-world-logo");
                stream.drawImage(logo, x, atY, width, height);
            }
        }

        private float wrapped(String value, float x, float startY, float width, PDFont font,
                              float size, PDColor color, float leading) throws IOException {
            String[] words = safe(value).split("\\s+");
            StringBuilder line = new StringBuilder();
            float currentY = startY;
            for (String word : words) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (font.getStringWidth(candidate) / 1000 * size > width && !line.isEmpty()) {
                    text(line.toString(), x, currentY, font, size, color);
                    currentY -= leading;
                    line = new StringBuilder(word);
                } else line = new StringBuilder(candidate);
            }
            if (!line.isEmpty()) text(line.toString(), x, currentY, font, size, color);
            return currentY - leading;
        }

        private void label(String value, float x, float atY) throws IOException { text(value, x, atY, BOLD, 7, MUTED); }
        private void text(String value, float x, float atY, PDFont font, float size, PDColor color) throws IOException {
            stream.beginText(); stream.setFont(font, size); stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, atY); stream.showText(asPdfText(value)); stream.endText();
        }
        private void right(String value, float right, float atY, PDFont font, float size, PDColor color) throws IOException {
            float width = font.getStringWidth(asPdfText(value)) / 1000 * size;
            text(value, right - width, atY, font, size, color);
        }
        private void line(float x1, float y1, float x2, float y2) throws IOException {
            stream.setStrokingColor(new PDColor(new float[]{.87f, .86f, .82f}, PDDeviceRGB.INSTANCE));
            stream.setLineWidth(.5f); stream.moveTo(x1, y1); stream.lineTo(x2, y2); stream.stroke();
        }
        private void close() throws IOException { stream.close(); }
    }

    private static String amount(BigDecimal value) {
        return "INR " + (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private static BigDecimal nvl(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private static String amountInWords(BigDecimal value) {
        BigDecimal rounded = nvl(value).setScale(2, RoundingMode.HALF_UP);
        long rupees = rounded.longValue();
        int paise = rounded.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
        String words = numberWords(rupees) + " rupees";
        if (paise > 0) words += " and " + numberWords(paise) + " paise";
        return capitalize(words) + " only";
    }

    private static String numberWords(long number) {
        if (number == 0) return "zero";
        if (number < 0) return "minus " + numberWords(-number);
        StringBuilder value = new StringBuilder();
        appendScale(value, number / 10_000_000, "crore"); number %= 10_000_000;
        appendScale(value, number / 100_000, "lakh"); number %= 100_000;
        appendScale(value, number / 1_000, "thousand"); number %= 1_000;
        appendScale(value, number / 100, "hundred"); number %= 100;
        if (number > 0) {
            if (!value.isEmpty()) value.append(" ");
            String[] ones = {"", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
                    "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
            String[] tens = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
            if (number < 20) value.append(ones[(int) number]);
            else {
                value.append(tens[(int) number / 10]);
                if (number % 10 > 0) value.append(" ").append(ones[(int) number % 10]);
            }
        }
        return value.toString();
    }

    private static void appendScale(StringBuilder target, long count, String scale) {
        if (count == 0) return;
        if (!target.isEmpty()) target.append(" ");
        target.append(numberWords(count)).append(" ").append(scale);
    }
    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
    private static String optional(String decorated, String value) { return value == null || value.isBlank() ? "" : decorated; }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String clip(String value, int max) { String safe = safe(value); return safe.length() <= max ? safe : safe.substring(0, max - 3) + "..."; }
    private static String asPdfText(String value) { return safe(value).replaceAll("[^\\x20-\\x7E]", "?"); }
    private static PDColor white() { return new PDColor(new float[]{1, 1, 1}, PDDeviceRGB.INSTANCE); }
}

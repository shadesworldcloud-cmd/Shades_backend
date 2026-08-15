package com.sunglassstore.service;

import com.sunglassstore.dto.response.AdminOrderResponse;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.service.impl.InvoiceServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceServiceImplTest {
    private final InvoiceServiceImpl service = new InvoiceServiceImpl(
            "Shades World", "shadesworldindia11@gmail.com",
            "Shop 1, Plot 366, Lane 2, Raja Park, Jaipur, Rajasthan - 302004",
            "8233511042", "08CFEPJ4650Q1ZX", "CFEPJ4650Q", "Rajasthan - 08",
            "www.shadesworld.online", "90041000", new BigDecimal("18.00"));

    @Test
    void paidOrderProducesReadableMultipagePdfWithSnapshotAmounts() throws Exception {
        AdminOrderResponse order = order("PAID", 28);

        byte[] bytes = service.generate(order);

        String previewPath = System.getProperty("invoice.preview.path");
        if (previewPath != null && !previewPath.isBlank()) {
            Path destination = Path.of(previewPath);
            Files.createDirectories(destination.getParent());
            Files.write(destination, bytes);
        }

        assertTrue(bytes.length > 2_000);
        assertEquals("%PDF", new String(bytes, 0, 4));
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(document.getNumberOfPages() > 1);
            assertTrue(text.contains("INVOICE"));
            assertTrue(text.contains("SW-42"));
            assertTrue(text.contains("08CFEPJ4650Q1ZX"));
            assertTrue(text.contains("90041000"));
            assertTrue(text.contains("Customer One"));
            assertTrue(text.contains("Classic Frame 1"));
            assertTrue(text.contains("INR 2378.20"));
            assertTrue(text.contains("MOCK-PAID-42"));
            assertTrue(text.contains("Two thousand three hundred seventy eight rupees and twenty paise only"));
            assertTrue(text.contains("Authorised Signatory"));
        }
    }

    @Test
    void unpaidOrderCannotGenerateInvoice() {
        assertThrows(BadRequestException.class, () -> service.generate(order("PENDING", 1)));
    }

    @Test
    void typicalPaidOrderFitsOneReadablePage() throws Exception {
        byte[] bytes = service.generate(order("PAID", 2));
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertEquals(1, document.getNumberOfPages());
        }
        String previewPath = System.getProperty("invoice.compact.preview.path");
        if (previewPath != null && !previewPath.isBlank()) {
            Path destination = Path.of(previewPath);
            Files.createDirectories(destination.getParent());
            Files.write(destination, bytes);
        }
    }

    private AdminOrderResponse order(String paymentStatus, int itemCount) {
        LocalDateTime purchasedAt = LocalDateTime.of(2026, 8, 4, 12, 30);
        List<AdminOrderResponse.Item> items = new ArrayList<>();
        for (int index = 1; index <= itemCount; index++) {
            items.add(new AdminOrderResponse.Item((long) index, "Classic Frame " + index,
                    "SW-CF-" + index, "Onyx", 1, new BigDecimal("75.00"), new BigDecimal("13.50"),
                    BigDecimal.ZERO, new BigDecimal("75.00")));
        }
        BigDecimal subtotal = new BigDecimal("75.00").multiply(BigDecimal.valueOf(itemCount));
        BigDecimal discount = itemCount == 28 ? new BigDecimal("100.00") : BigDecimal.ZERO;
        BigDecimal tax = subtotal.subtract(discount).multiply(new BigDecimal("0.18")).setScale(2);
        BigDecimal shipping = itemCount == 28 ? new BigDecimal("18.20") : new BigDecimal("49.00");
        BigDecimal total = subtotal.subtract(discount).add(tax).add(shipping);
        return new AdminOrderResponse(42L, "CONFIRMED", subtotal,
                discount, tax, shipping, total, purchasedAt, null, purchasedAt,
                new AdminOrderResponse.Customer(7L, "Customer One", "customer@example.com", "9999999999"),
                new AdminOrderResponse.ShippingAddress("Customer One", "9999999999", "12 Long Street",
                        "Apartment 4", "Barcelona", "Catalonia", "08001", "Spain"),
                items,
                List.of(new AdminOrderResponse.PaymentInfo(3L, total, "MOCK",
                        paymentStatus, "MOCK", "MOCK-PAID-42", purchasedAt, purchasedAt)),
                // No automatic offer and no coupon: these fixtures exist to check the invoice's
                // layout and arithmetic, and the offer-labelled discount line has its own test.
                List.of(), List.of(), null, null);
    }
}

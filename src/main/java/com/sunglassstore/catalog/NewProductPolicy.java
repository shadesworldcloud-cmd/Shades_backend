package com.sunglassstore.catalog;

import com.sunglassstore.entity.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * The single definition of when a product is "New".
 *
 * This exists because the badge used to be computed in the browser, in
 * {@code StoreContext.mapProduct}, as {@code Date.now() - new Date(createdAt) < 30 * 86400000}.
 * That had four problems, and each one is a requirement this class answers:
 *
 * <ol>
 *   <li>It ran on the client clock, so a customer with a skewed system time saw a different
 *       catalogue to everyone else. The comparison now happens once, on the server.</li>
 *   <li>{@code createdAt} is serialised as a zone-less {@code LocalDateTime}; the browser parsed
 *       it as local time while the server had written it in the server's zone. The comparison is
 *       now UTC on both sides of the subtraction.</li>
 *   <li>The 30-day window was a literal in a mapping function. It is now
 *       {@code app.catalog.new-product-days}.</li>
 *   <li>It measured from row creation, not publication, and it never checked whether the product
 *       was actually on sale. {@link Product#getPublishedAt()} is null until first activation, so
 *       drafts cannot be New.</li>
 * </ol>
 *
 * The rule: a product is New when it is active and was published strictly less than
 * {@code newProductDays} ago in UTC. The boundary is therefore exclusive — at exactly N days the
 * badge is gone. Age is measured in whole days from the publication instant rather than from
 * midnight, so the badge does not flip early or late for customers in other timezones.
 */
@Component
public class NewProductPolicy {

    private final int newProductDays;

    public NewProductPolicy(@Value("${app.catalog.new-product-days:30}") int newProductDays) {
        this.newProductDays = newProductDays;
    }

    public int getNewProductDays() {
        return newProductDays;
    }

    public boolean isNew(Product product) {
        return isNew(product, Instant.now());
    }

    /** Clock injected so the boundary is testable without waiting 30 days. */
    public boolean isNew(Product product, Instant now) {
        if (product == null || !Boolean.TRUE.equals(product.getIsActive())) {
            return false;
        }
        Instant publishedAt = product.getPublishedAt();
        if (publishedAt == null) {
            return false;
        }
        // Exclusive upper bound: a product published exactly newProductDays ago is no longer New.
        return publishedAt.isAfter(now.minus(Duration.ofDays(newProductDays)));
    }
}

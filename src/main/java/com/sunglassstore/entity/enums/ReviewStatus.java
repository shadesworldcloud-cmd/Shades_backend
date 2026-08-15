package com.sunglassstore.entity.enums;

/**
 * Review lifecycle.
 *
 * Reviews are published on submission — eligibility (a delivered purchase of that exact variant,
 * not returned or refunded) is the gate, not a moderator. Moderation is now reactive: it exists to
 * take an abusive review down, never to let a legitimate one up.
 *
 * Persisted with EnumType.STRING, so the order of these constants is not significant.
 */
public enum ReviewStatus {
    /** Live and publicly visible. The state every new review is created in. */
    PUBLISHED,

    /** Explicitly approved by a moderator. Publicly visible; kept for pre-existing rows and for a
     *  moderator reinstating something they had rejected. */
    APPROVED,

    /** Hidden, awaiting re-moderation. Only reachable by editing a rejected review, so a takedown
     *  cannot be undone simply by editing the text. */
    PENDING,

    /** Moderated away for abuse. Never publicly visible. */
    REJECTED;

    /** The statuses a shopper is allowed to see. */
    public boolean isPubliclyVisible() {
        return this == PUBLISHED || this == APPROVED;
    }
}

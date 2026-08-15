package com.sunglassstore.dto.response;

public record EmailOutboxSummaryResponse(long total, long pending, long retry, long sent, long failed) {
}

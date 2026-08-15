package com.sunglassstore.exception;

/**
 * Raised when a caller tried to update a record using a version that is no longer current — someone
 * else committed a change in between.
 *
 * Distinct from ConflictException (which means "this would duplicate something") because the
 * remedy is different and the message has to say so: the caller must re-read and re-decide, not
 * change their input. Never retried automatically — the whole point is that the new value was
 * chosen against data that has since changed, so replaying it would be the silent overwrite this
 * exists to prevent.
 */
public class OptimisticLockConflictException extends RuntimeException {
    public OptimisticLockConflictException(String message) {
        super(message);
    }
}

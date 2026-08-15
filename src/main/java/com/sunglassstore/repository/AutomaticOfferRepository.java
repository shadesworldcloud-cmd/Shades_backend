package com.sunglassstore.repository;

import com.sunglassstore.entity.AutomaticOffer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AutomaticOfferRepository extends JpaRepository<AutomaticOffer, Long> {

    Page<AutomaticOffer> findAllByOrderByAutomaticOfferIdDesc(Pageable pageable);

    /**
     * The offer in force at {@code now}.
     *
     * The database already permits only one active, unarchived offer, so this returns at most one
     * row today. The ORDER BY is still spelled out because a query that would become ambiguous if
     * the singleton rule were ever relaxed is a query that silently starts depending on row order —
     * highest priority first, then the oldest offer, both total and stable.
     *
     * The window is compared against a timestamp the caller supplies rather than NOW(), so a single
     * transaction can price the cart, the checkout and the order against one instant instead of
     * three, and a test can pin it.
     */
    @Query("""
            SELECT o FROM AutomaticOffer o
            WHERE o.isActive = true
              AND o.archivedAt IS NULL
              AND o.startsAt <= :now
              AND o.endsAt > :now
            ORDER BY o.priority DESC, o.automaticOfferId ASC
            """)
    List<AutomaticOffer> findEffective(@Param("now") LocalDateTime now, Pageable pageable);

    /** The active offer regardless of its window, i.e. the holder of the single active slot. */
    @Query("SELECT o FROM AutomaticOffer o WHERE o.isActive = true AND o.archivedAt IS NULL")
    Optional<AutomaticOffer> findActive();

    /**
     * Row lock for the write paths. Activation has to read "is the slot taken" and then write, and
     * two administrators doing that at once would otherwise both pass the read; the unique index
     * would still refuse the second insert, but this turns a raw duplicate-key error into the 409
     * the API is supposed to return.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM AutomaticOffer o WHERE o.automaticOfferId = :id")
    Optional<AutomaticOffer> findByIdForUpdate(@Param("id") Long id);
}

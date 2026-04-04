package com.cursor_springa_ai.playground.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Composite primary key for {@link HoldingSnapshot}: one row per user × instrument × calendar date.
 */
@Embeddable
public class HoldingSnapshotId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    protected HoldingSnapshotId() {
    }

    public HoldingSnapshotId(Long userId, Long instrumentId, LocalDate snapshotDate) {
        this.userId = userId;
        this.instrumentId = instrumentId;
        this.snapshotDate = snapshotDate;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HoldingSnapshotId that)) return false;
        return Objects.equals(userId, that.userId)
                && Objects.equals(instrumentId, that.instrumentId)
                && Objects.equals(snapshotDate, that.snapshotDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, instrumentId, snapshotDate);
    }
}

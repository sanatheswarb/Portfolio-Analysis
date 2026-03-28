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

    @Column(name = "instrument_token", nullable = false)
    private Long instrumentToken;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    protected HoldingSnapshotId() {
    }

    public HoldingSnapshotId(Long userId, Long instrumentToken, LocalDate snapshotDate) {
        this.userId = userId;
        this.instrumentToken = instrumentToken;
        this.snapshotDate = snapshotDate;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getInstrumentToken() {
        return instrumentToken;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HoldingSnapshotId that)) return false;
        return Objects.equals(userId, that.userId)
                && Objects.equals(instrumentToken, that.instrumentToken)
                && Objects.equals(snapshotDate, that.snapshotDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, instrumentToken, snapshotDate);
    }
}

package com.cursor_springa_ai.playground.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Append-only audit log of every AI portfolio analysis result.
 * A new row is written on every successful call to the analysis API — rows are
 * never updated or deleted.
 *
 * <p>{@code analysis_data} is stored as JSONB in PostgreSQL (Hibernate maps it
 * via {@code @JdbcTypeCode(SqlTypes.JSON)} which uses the dialect-appropriate
 * binary-JSON type in PostgreSQL and CLOB/text in H2 for tests).
 */
@Entity
@Table(name = "ai_analysis")
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning user — nullable so that analyses run before login are still stored. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Logical category of the analysis, e.g. {@code "PORTFOLIO_ADVICE"}.
     * Kept as a plain VARCHAR for flexibility.
     */
    @Column(name = "analysis_type", nullable = false, length = 50)
    private String analysisType;

    @Column(name = "question")
    private String question;

    /**
     * Full AI response payload serialised as JSON.
     * Stored as JSONB in PostgreSQL for efficient querying.
     */
    @Column(name = "analysis_data", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String analysisData;

    @Column(name = "analysis_context")
    @JdbcTypeCode(SqlTypes.JSON)
    private String analysisContext;

    @Column(name = "parent_analysis_id")
    private Long parentAnalysisId;

    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column(name = "analysis_version", length = 50)
    private String analysisVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AiAnalysis() {
    }

    public AiAnalysis(User user, String analysisType, String analysisData) {
        this(user, analysisType, null, analysisData, null, null, null, null);
    }

    public AiAnalysis(
            User user,
            String analysisType,
            String question,
            String analysisData,
            String analysisContext,
            Long parentAnalysisId,
            String modelUsed,
            String analysisVersion
    ) {
        this.user = user;
        this.analysisType = analysisType;
        this.question = question;
        this.analysisData = analysisData;
        this.analysisContext = analysisContext;
        this.parentAnalysisId = parentAnalysisId;
        this.modelUsed = modelUsed;
        this.analysisVersion = analysisVersion;
    }

    public Long getId() { return id; }

    public User getUser() { return user; }

    public String getAnalysisType() { return analysisType; }

    public String getQuestion() { return question; }

    public String getAnalysisData() { return analysisData; }

    public String getAnalysisContext() { return analysisContext; }

    public Long getParentAnalysisId() { return parentAnalysisId; }

    public String getModelUsed() { return modelUsed; }

    public String getAnalysisVersion() { return analysisVersion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}

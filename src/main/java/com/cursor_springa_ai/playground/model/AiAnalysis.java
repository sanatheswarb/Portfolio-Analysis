package com.cursor_springa_ai.playground.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Append-only audit log of every AI interaction — portfolio analysis results
 * and follow-up chat messages.
 *
 * <p>{@code analysis_data} and {@code analysis_context} are stored as JSONB in
 * PostgreSQL (Hibernate maps them via {@code @JdbcTypeCode(SqlTypes.JSON)}).
 *
 * <p>Chat rows link back to their base analysis via {@code parent_analysis_id}
 * so the full conversation chain can be reconstructed.
 */
@Entity
@Table(
    name = "ai_analysis",
    indexes = {
        @Index(name = "idx_ai_analysis_user_date",  columnList = "user_id,created_at"),
        @Index(name = "idx_ai_analysis_parent",     columnList = "parent_analysis_id")
    }
)
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning user — nullable so that analyses run before login are still stored. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Type-safe category of the AI interaction. */
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 50)
    private AnalysisType analysisType;

    /**
     * Free-text question asked by the user.  Only populated for chat rows
     * ({@link AnalysisType#PORTFOLIO_CHAT} etc.); {@code null} for analysis rows.
     */
    @Column(name = "question", length = 1000)
    private String question;

    /**
     * Full AI response payload serialised as JSON.
     * Stored as JSONB in PostgreSQL for efficient querying.
     */
    @Column(name = "analysis_data", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String analysisData;

    /**
     * Lean snapshot of the reasoning context used by the AI.
     * Populated for analysis rows; chat rows reuse the parent's context.
     */
    @Column(name = "analysis_context")
    @JdbcTypeCode(SqlTypes.JSON)
    private String analysisContext;

    /**
     * Links a chat row back to its base portfolio-analysis row.
     * {@code null} for top-level analysis rows.
     */
    @Column(name = "parent_analysis_id")
    private Long parentAnalysisId;

    /** Model identifier used to generate this response, e.g. {@code qwen2.5:7b}. */
    @Column(name = "model_used", length = 50)
    private String modelUsed;

    /**
     * Prompt/logic version tag, e.g. {@code v1_prompt}.
     * Useful for comparing outputs across prompt iterations.
     */
    @Column(name = "analysis_version", length = 50)
    private String analysisVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AiAnalysis() {
    }

    /**
     * Full constructor used by {@code AiAnalysisService} for analysis rows.
     *
     * @param user              owning user (may be null)
     * @param analysisType      type of AI interaction
     * @param question          user question (null for analysis rows)
     * @param analysisData      serialised AI response JSON
     * @param analysisContext   serialised reasoning-context snapshot JSON (null for chat rows)
     * @param modelUsed         model identifier, e.g. {@code qwen2.5:7b-instruct}
     * @param analysisVersion   prompt/logic version tag, e.g. {@code V1}
     */
    public AiAnalysis(User user, AnalysisType analysisType, String question,
                      String analysisData, String analysisContext,
                      String modelUsed, String analysisVersion) {
        this.user = user;
        this.analysisType = analysisType;
        this.question = question;
        this.analysisData = analysisData;
        this.analysisContext = analysisContext;
        this.modelUsed = modelUsed;
        this.analysisVersion = analysisVersion;
    }

    public Long getId() { return id; }

    public User getUser() { return user; }

    public AnalysisType getAnalysisType() { return analysisType; }

    public String getQuestion() { return question; }

    public String getAnalysisData() { return analysisData; }

    public String getAnalysisContext() { return analysisContext; }

    public Long getParentAnalysisId() { return parentAnalysisId; }

    public String getModelUsed() { return modelUsed; }

    public String getAnalysisVersion() { return analysisVersion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}

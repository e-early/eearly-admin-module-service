package si.result.project.eearly.model.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import si.result.project.eearly.model.auditable.Auditable;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Table(name = "t_analysis_detection_box")
public class AnalysisDetectionBox extends Auditable {

    @Id
    @UuidGenerator
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_analysis", nullable = false)
    private Analysis analysis;

    @Column(name = "start_timestamp", nullable = false)
    private OffsetDateTime startTimestamp;

    @Column(name = "end_timestamp", nullable = false)
    private OffsetDateTime endTimestamp;

    @Column(name = "original_start_timestamp", nullable = false)
    private OffsetDateTime originalStartTimestamp;

    @Column(name = "original_end_timestamp", nullable = false)
    private OffsetDateTime originalEndTimestamp;

    @Column(name = "probability")
    private Double probability;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "caretaker_feedback")
    private FeedbackState caretakerFeedback;

    @Column(name = "source", length = 64)
    private String source;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "audit_status", nullable = false)
    private DetectionBoxAuditStatus auditStatus;

    public static AnalysisDetectionBox create(
            final Analysis analysis,
            final OffsetDateTime startTimestamp,
            final OffsetDateTime endTimestamp,
            final Double probability,
            final FeedbackState caretakerFeedback,
            final String source) {
        return AnalysisDetectionBox.builder()
                .analysis(analysis)
                .startTimestamp(startTimestamp)
                .endTimestamp(endTimestamp)
                .originalStartTimestamp(startTimestamp)
                .originalEndTimestamp(endTimestamp)
                .probability(probability)
                .caretakerFeedback(caretakerFeedback)
                .source(source)
                .auditStatus(DetectionBoxAuditStatus.CREATED)
                .build();
    }

    public void updateDisplayedInterval(final OffsetDateTime startTimestamp, final OffsetDateTime endTimestamp) {
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        markEdited();
    }

    public void markEdited() {
        this.auditStatus = DetectionBoxAuditStatus.EDITED;
    }

    public void markDeleted() {
        this.auditStatus = DetectionBoxAuditStatus.DELETED;
        this.isDeleted = true;
    }
}

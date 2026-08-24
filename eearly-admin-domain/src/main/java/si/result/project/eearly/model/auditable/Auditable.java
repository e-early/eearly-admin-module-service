package si.result.project.eearly.model.auditable;

import com.google.common.base.MoreObjects;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.ZonedDateTime;
import java.util.Objects;
import lombok.Getter;
import org.hibernate.annotations.OptimisticLock;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class Auditable {

  @CreatedBy
  @OptimisticLock(excluded = true)
  @Column(name = "created_by", updatable = false)
  protected String createdBy;

  @CreatedDate
  @OptimisticLock(excluded = true)
  @Column(name = "created_at", updatable = false)
  protected ZonedDateTime createdAt;

  @LastModifiedBy
  @OptimisticLock(excluded = true)
  @Column(name = "updated_by")
  protected String updatedBy;

  @LastModifiedDate
  @OptimisticLock(excluded = true)
  @Column(name = "updated_at")
  protected ZonedDateTime lastModifiedAt;

  @Column(name = "is_deleted")
  protected boolean isDeleted;

  @Version
  protected long version;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Auditable auditable)) {
      return false;
    }
    return isDeleted == auditable.isDeleted && version == auditable.version && Objects.equals(createdBy,
        auditable.createdBy) && Objects.equals(createdAt, auditable.createdAt) && Objects.equals(
        updatedBy, auditable.updatedBy) && Objects.equals(lastModifiedAt, auditable.lastModifiedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(createdBy, createdAt, updatedBy, lastModifiedAt, isDeleted, version);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("createdBy", createdBy)
        .add("createdAt", createdAt)
        .add("updatedBy", updatedBy)
        .add("lastModifiedAt", lastModifiedAt)
        .add("isDeleted", isDeleted)
        .add("version", version)
        .toString();
  }
}

package ru.shaxowskiy.javaspeakerclub.audio;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class AudioAnalysisRepository {

    private final JdbcTemplate jdbc;

    boolean create(UUID id, UUID mediaId, String owner) {
        return jdbc.update("""
                INSERT INTO audio_analyses (id, media_id, status)
                SELECT ?, media.id, 'QUEUED'
                FROM user_media media JOIN users u ON u.id = media.user_id
                WHERE media.id = ? AND u.username = ?
                FOR KEY SHARE OF media
                """, id, mediaId, owner) == 1;
    }

    Optional<Job> find(UUID id, String owner) {
        return jdbc.query("""
                SELECT job.id, job.media_id, CASE WHEN expired.value
                           THEN CASE WHEN job.attempts >= 3 THEN 'FAILED' ELSE 'QUEUED' END
                           ELSE job.status END AS status, job.report::text,
                       CASE WHEN expired.value AND job.attempts >= 3
                           THEN 'LEASE_EXPIRED' ELSE job.error_code END AS error_code,
                       job.created_at, job.updated_at
                FROM audio_analyses job JOIN user_media media ON media.id = job.media_id
                JOIN users u ON u.id = media.user_id
                CROSS JOIN LATERAL (SELECT job.status = 'RUNNING'
                    AND job.lease_until <= CURRENT_TIMESTAMP AS value) expired
                WHERE job.id = ? AND u.username = ?
                """, (row, index) -> new Job(
                row.getObject("id", UUID.class), row.getObject("media_id", UUID.class),
                row.getString("status"), row.getString("report"), row.getString("error_code"),
                row.getObject("created_at", OffsetDateTime.class),
                row.getObject("updated_at", OffsetDateTime.class)), id, owner).stream().findFirst();
    }

    void expireExhausted() {
        jdbc.update("""
                UPDATE audio_analyses
                SET status = 'FAILED', error_code = 'LEASE_EXPIRED',
                    lease_id = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP AND attempts >= 3
                """);
    }

    Optional<Claim> claim(UUID leaseId) {
        return jdbc.query("""
                WITH next_job AS (
                    SELECT id FROM audio_analyses
                    WHERE status = 'QUEUED'
                       OR (status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP AND attempts < 3)
                    ORDER BY created_at, id
                    LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE audio_analyses job
                SET status = 'RUNNING', attempts = attempts + 1, lease_id = ?,
                    lease_until = CURRENT_TIMESTAMP + INTERVAL '15 minutes',
                    updated_at = CURRENT_TIMESTAMP
                FROM next_job, user_media media
                WHERE job.id = next_job.id AND media.id = job.media_id
                RETURNING job.id, media.media_minio_key
                """, (row, index) -> new Claim(row.getObject("id", UUID.class),
                row.getString("media_minio_key")), leaseId).stream().findFirst();
    }

    boolean finish(UUID id, UUID leaseId, String status, String report, String errorCode) {
        return jdbc.update("""
                UPDATE audio_analyses
                SET status = ?, report = CAST(? AS jsonb), error_code = ?,
                    lease_id = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'RUNNING' AND lease_id = ?
                  AND lease_until > CURRENT_TIMESTAMP
                """, status, report, errorCode, id, leaseId) == 1;
    }

    record Claim(UUID id, String objectKey) {
    }

    record Job(UUID id, UUID mediaId, String status, String report, String errorCode,
               OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
}

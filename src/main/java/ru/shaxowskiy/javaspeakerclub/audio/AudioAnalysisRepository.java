package ru.shaxowskiy.javaspeakerclub.audio;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class AudioAnalysisRepository {

    private final JdbcTemplate jdbc;

    // Shared projection keeps expired leases consistent in both the list and the detail response.
    private static final String JOB_QUERY = """
                SELECT job.id, job.media_id, CASE WHEN expired.value
                           THEN CASE WHEN job.attempts >= 3 THEN 'FAILED' ELSE 'QUEUED' END
                           ELSE job.status END AS status, %s AS report,
                       CASE WHEN expired.value AND job.attempts >= 3
                           THEN 'LEASE_EXPIRED' ELSE job.error_code END AS error_code,
                       job.created_at, job.updated_at
                FROM audio_analyses job JOIN user_media media ON media.id = job.media_id
                JOIN users u ON u.id = media.user_id
                CROSS JOIN LATERAL (SELECT job.status = 'RUNNING'
                    AND job.lease_until <= CURRENT_TIMESTAMP AS value) expired
                WHERE u.username = ?
                """;
    private static final RowMapper<Job> JOB_MAPPER = (row, index) -> new Job(
            row.getObject("id", UUID.class), row.getObject("media_id", UUID.class),
            row.getString("status"), row.getString("report"), row.getString("error_code"),
            row.getObject("created_at", OffsetDateTime.class),
            row.getObject("updated_at", OffsetDateTime.class));

    @Transactional
    public Optional<Job> createOrFindActive(UUID mediaId, String owner) {
        // Serialize starts for this file, including concurrent requests from different tabs.
        var media = jdbc.query("""
                SELECT media.id
                FROM user_media media JOIN users u ON u.id = media.user_id
                WHERE media.id = ? AND u.username = ?
                FOR UPDATE OF media
                """, (row, index) -> row.getObject("id", UUID.class), mediaId, owner);
        if (media.isEmpty()) return Optional.empty();
        var active = jdbc.query(JOB_QUERY.formatted("job.report::text") + """
                AND job.media_id = ? AND job.status IN ('QUEUED', 'RUNNING')
                AND NOT (job.status = 'RUNNING' AND job.lease_until <= CURRENT_TIMESTAMP AND job.attempts >= 3)
                ORDER BY job.created_at DESC, job.id DESC LIMIT 1
                """, JOB_MAPPER, owner, mediaId).stream().findFirst();
        if (active.isPresent()) return active;
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO audio_analyses (id, media_id, status) VALUES (?, ?, 'QUEUED')", id, mediaId);
        return find(id, owner);
    }

    Optional<Job> find(UUID id, String owner) {
        return jdbc.query(JOB_QUERY.formatted("job.report::text") + " AND job.id = ?",
                JOB_MAPPER, owner, id).stream().findFirst();
    }

    List<Job> latest(String owner, UUID mediaId) {
        // No transcripts in the library response: only the newest status of each file.
        return jdbc.query("SELECT DISTINCT ON (media_id) * FROM (" + JOB_QUERY.formatted("NULL::text") + """
                AND (CAST(? AS UUID) IS NULL OR job.media_id = ?)
                ) owned ORDER BY media_id, created_at DESC, id DESC
                """, JOB_MAPPER, owner, mediaId, mediaId);
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

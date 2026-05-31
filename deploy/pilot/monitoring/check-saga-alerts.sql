-- EA-033 Saga alert queries (A-03 / A-04)
-- A-03: outbox FAILED > 0 (optionally stale > 30 min)
-- A-04: compensation MANUAL_REQUIRED > 0

SET search_path TO scf;

\echo '=== A-03 Outbox FAILED ==='
SELECT event_status, COUNT(*) AS cnt,
       MAX(updated_at) AS last_updated
FROM biz_event_outbox
WHERE event_status = 'FAILED'
GROUP BY event_status;

\echo '=== A-03 Outbox FAILED stale > 30 min ==='
SELECT COUNT(*) AS stale_failed_cnt
FROM biz_event_outbox
WHERE event_status = 'FAILED'
  AND updated_at < NOW() - INTERVAL '30 minutes';

\echo '=== A-04 Compensation MANUAL_REQUIRED ==='
SELECT compensation_status, COUNT(*) AS cnt,
       MAX(updated_at) AS last_updated
FROM biz_compensation_task
WHERE compensation_status = 'MANUAL_REQUIRED'
GROUP BY compensation_status;

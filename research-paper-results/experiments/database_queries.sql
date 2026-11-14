-- ==============================================================================
-- Urban Pulse – Research Paper Database Audit SQL Queries
-- Target Database: PostgreSQL 18 ('urbanpulse')
-- Purpose: Extract read-only operational telemetry, triage distributions, and SLA metrics
-- ==============================================================================

-- 1. Total Complaint Volume & Table Footprint
SELECT 'complaints' AS table_name, count(*) AS total_rows FROM complaints
UNION ALL
SELECT 'complaint_status_history', count(*) FROM complaint_status_history
UNION ALL
SELECT 'complaint_participants', count(*) FROM complaint_participants
UNION ALL
SELECT 'departments', count(*) FROM departments
UNION ALL
SELECT 'users', count(*) FROM users;

-- 2. Complaint Volume by Operational Lifecycle Status
SELECT 
    status, 
    count(*) AS count, 
    round((count(*) * 100.0 / (SELECT count(*) FROM complaints)), 2) AS percentage
FROM complaints 
GROUP BY status 
ORDER BY count DESC;

-- 3. Ingestion & Automated Dispatch Method Distribution
SELECT 
    COALESCE(assignment_method, 'LEGACY_UNSET') AS assignment_method, 
    count(*) AS count,
    round((count(*) * 100.0 / (SELECT count(*) FROM complaints)), 2) AS percentage
FROM complaints 
GROUP BY assignment_method 
ORDER BY count DESC;

-- 4. Complaints Escalated to Administrative Manual Review Queue
SELECT 
    CASE 
        WHEN requires_manual_review = true THEN 'ESCALATED_MANUAL_REVIEW'
        WHEN requires_manual_review = false THEN 'AUTOMATED_ROUTING'
        ELSE 'LEGACY_UNSET'
    END AS review_status,
    count(*) AS count,
    round((count(*) * 100.0 / (SELECT count(*) FROM complaints)), 2) AS percentage
FROM complaints 
GROUP BY requires_manual_review;

-- 5. Complaint Breakdown by Civic Grievance Category
SELECT 
    issue_type, 
    count(*) AS count,
    round((count(*) * 100.0 / (SELECT count(*) FROM complaints)), 2) AS percentage
FROM complaints 
GROUP BY issue_type 
ORDER BY count DESC;

-- 6. SLA Compliance Among Resolved Complaints
SELECT 
    CASE 
        WHEN resolved_within_sla = true THEN 'RESOLVED_ON_TIME'
        WHEN resolved_within_sla = false THEN 'RESOLVED_OVERDUE'
        ELSE 'SLA_NOT_RECORDED'
    END AS sla_resolution_status,
    count(*) AS count,
    round((count(*) * 100.0 / (SELECT count(*) FROM complaints WHERE status = 'RESOLVED')), 2) AS percentage_of_resolved
FROM complaints 
WHERE status = 'RESOLVED' 
GROUP BY resolved_within_sla;

-- 7. Municipal Role & Staff Distribution
SELECT 
    role, 
    count(*) AS count,
    round((count(*) * 100.0 / (SELECT count(*) FROM users)), 2) AS percentage
FROM users 
GROUP BY role 
ORDER BY count DESC;

-- 8. Departmental Staffing & Supervisor Coverage
SELECT 
    d.id AS department_id,
    d.name AS department_name,
    count(u.mobile_number) FILTER (WHERE u.role = 'SUPERVISOR') AS supervisor_count,
    count(u.mobile_number) FILTER (WHERE u.role = 'WORKER') AS worker_count
FROM departments d
LEFT JOIN users u ON u.department_id = d.id
GROUP BY d.id, d.name
ORDER BY d.id;

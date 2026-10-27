-- ==============================================================================
-- DBA SCHEMA & COMPATIBILITY VERIFICATION SCRIPT (READ-ONLY)
-- Project: prolt-operations (Node.js/Prisma -> Spring Boot / Flyway Migration)
-- Target: Production PostgreSQL Database
-- Purpose: Extract non-sensitive, sanitized schema inventory, constraint definitions,
--          migration history, and duplicate detection metrics for migration readiness.
-- Safety: READ-ONLY. Zero UPDATE, INSERT, DELETE, DROP, or ALTER operations.
-- ==============================================================================

\echo '=== 1. SCHEMA INVENTORY ==='
SELECT schema_name 
FROM information_schema.schemata 
WHERE schema_name IN ('public', 'forecasting')
ORDER BY schema_name;

\echo '=== 2. TABLE INVENTORY & COLUMN COUNTS ==='
SELECT table_schema, table_name, COUNT(column_name) AS column_count
FROM information_schema.columns
WHERE table_schema IN ('public', 'forecasting')
GROUP BY table_schema, table_name
ORDER BY table_schema, table_name;

\echo '=== 3. TOTAL TABLE COUNT ==='
SELECT table_schema, COUNT(DISTINCT table_name) AS table_count
FROM information_schema.tables
WHERE table_schema IN ('public', 'forecasting')
  AND table_type = 'BASE TABLE'
GROUP BY table_schema;

\echo '=== 4. PRIMARY KEY CONSTRAINTS ==='
SELECT tc.table_schema, tc.table_name, tc.constraint_name, kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
  ON tc.constraint_name = kcu.constraint_name 
 AND tc.table_schema = kcu.table_schema
WHERE tc.constraint_type = 'PRIMARY KEY'
  AND tc.table_schema IN ('public', 'forecasting')
ORDER BY tc.table_schema, tc.table_name, kcu.ordinal_position;

\echo '=== 5. UNIQUE CONSTRAINTS ==='
SELECT tc.table_schema, tc.table_name, tc.constraint_name, kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
  ON tc.constraint_name = kcu.constraint_name 
 AND tc.table_schema = kcu.table_schema
WHERE tc.constraint_type = 'UNIQUE'
  AND tc.table_schema IN ('public', 'forecasting')
ORDER BY tc.table_schema, tc.table_name, kcu.column_name;

\echo '=== 6. FOREIGN KEY CONSTRAINTS ==='
SELECT tc.table_schema, tc.table_name, tc.constraint_name, kcu.column_name,
       ccu.table_schema AS foreign_table_schema,
       ccu.table_name AS foreign_table_name,
       ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
  ON tc.constraint_name = kcu.constraint_name 
 AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage ccu 
  ON ccu.constraint_name = tc.constraint_name 
 AND ccu.table_schema = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_schema IN ('public', 'forecasting')
ORDER BY tc.table_schema, tc.table_name;

\echo '=== 7. COLUMN DEFINITIONS & TYPES FOR CRITICAL RESOURCE CENTER TABLES ==='
SELECT table_name, column_name, data_type, udt_name, numeric_precision, numeric_scale, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('ctu_charges', 'state_charges', 'state_tariff', 'fppa_charges', 'User', 'region_state', 'discom_list')
ORDER BY table_name, ordinal_position;

\echo '=== 8. POSTGRES ENUM TYPES ==='
SELECT n.nspname AS schema_name, t.typname AS enum_name, e.enumlabel AS enum_value
FROM pg_type t
JOIN pg_enum e ON t.oid = e.enumtypid
JOIN pg_namespace n ON n.oid = t.typnamespace
WHERE n.nspname IN ('public', 'forecasting')
ORDER BY n.nspname, t.typname, e.enumsortorder;

\echo '=== 9. PRISMA MIGRATION HISTORY (IF EXISTS) ==='
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '_prisma_migrations') THEN
        RAISE NOTICE 'Executing query on _prisma_migrations:';
    ELSE
        RAISE NOTICE '_prisma_migrations table does not exist.';
    END IF;
END $$;

SELECT id, migration_name, finished_at, rolled_back_at, applied_steps_count
FROM "public"."_prisma_migrations"
ORDER BY finished_at ASC;

\echo '=== 10. FLYWAY SCHEMA HISTORY (IF EXISTS) ==='
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'flyway_schema_history') THEN
        RAISE NOTICE 'Executing query on flyway_schema_history:';
    ELSE
        RAISE NOTICE 'flyway_schema_history table does not exist.';
    END IF;
END $$;

SELECT version, description, type, script, installed_on, success
FROM "public"."flyway_schema_history"
ORDER BY installed_rank ASC;

\echo '=== 11. AGGREGATE ROW COUNTS (SANITIZED - NO PII) ==='
SELECT 'ctu_charges' AS table_name, COUNT(*) AS row_count FROM "public"."ctu_charges"
UNION ALL
SELECT 'state_charges', COUNT(*) FROM "public"."state_charges"
UNION ALL
SELECT 'state_tariff', COUNT(*) FROM "public"."state_tariff"
UNION ALL
SELECT 'fppa_charges', COUNT(*) FROM "public"."fppa_charges"
UNION ALL
SELECT 'region_state', COUNT(*) FROM "public"."region_state"
UNION ALL
SELECT 'discom_list', COUNT(*) FROM "public"."discom_list"
UNION ALL
SELECT 'User', COUNT(*) FROM "public"."User"
UNION ALL
SELECT 'Dataset', COUNT(*) FROM "public"."Dataset"
UNION ALL
SELECT 'HolidayCalendar', COUNT(*) FROM "public"."HolidayCalendar";

\echo '=== 12. CTU CHARGES NATURAL KEY DUPLICATE CHECK ==='
SELECT state, month, COUNT(*) AS duplicate_count
FROM "public"."ctu_charges"
GROUP BY state, month
HAVING COUNT(*) > 1;

\echo '=== 13. STATE CHARGES CANDIDATE NATURAL KEY DUPLICATE CHECK ==='
SELECT state, COALESCE(discom, 'N/A') AS discom, COALESCE(category, 'N/A') AS category, 
       COALESCE(sub_category, 'N/A') AS sub_category, COALESCE(voltage_level, 'N/A') AS voltage_level,
       from_date, to_date, COUNT(*) AS duplicate_count
FROM "public"."state_charges"
GROUP BY state, COALESCE(discom, 'N/A'), COALESCE(category, 'N/A'), 
         COALESCE(sub_category, 'N/A'), COALESCE(voltage_level, 'N/A'), from_date, to_date
HAVING COUNT(*) > 1;

\echo '=== 14. CTU CHARGES FINANCIAL AGGREGATE CHECKSUM ==='
SELECT COUNT(*) AS total_records,
       ROUND(SUM(ctu_charges_rs_per_kwh), 4) AS sum_ctu_charges,
       ROUND(AVG(ctu_charges_rs_per_kwh), 6) AS avg_ctu_charges
FROM "public"."ctu_charges";

\echo '=== 15. STATE CHARGES FINANCIAL AGGREGATE CHECKSUM ==='
SELECT COUNT(*) AS total_records,
       ROUND(SUM(cross_subsidy), 4) AS sum_cross_subsidy,
       ROUND(SUM(distribution_wheeling_charges), 4) AS sum_distribution_wheeling,
       ROUND(SUM(stu_charges), 4) AS sum_stu_charges
FROM "public"."state_charges";

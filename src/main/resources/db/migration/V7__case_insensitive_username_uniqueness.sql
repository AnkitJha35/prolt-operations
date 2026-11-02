-- Migration: V7__case_insensitive_username_uniqueness.sql
-- Description: Enforce case-insensitive and whitespace-insensitive uniqueness on public."User"(username)

-- 1. Preflight safety check: abort migration if duplicate normalized usernames exist
DO $$
DECLARE
    dup_count INT;
BEGIN
    SELECT COUNT(*) INTO dup_count
    FROM (
        SELECT LOWER(TRIM("username"))
        FROM "public"."User"
        GROUP BY LOWER(TRIM("username"))
        HAVING COUNT(*) > 1
    ) d;

    IF dup_count > 0 THEN
        RAISE EXCEPTION 'Flyway migration V7 aborted: Found % duplicate normalized usernames in public."User". Resolve duplicates before migrating.', dup_count;
    END IF;
END $$;

-- 2. Safely drop legacy case-sensitive unique index/constraint if it exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public."User"'::regclass AND conname = 'User_username_key'
    ) THEN
        ALTER TABLE public."User" DROP CONSTRAINT "User_username_key";
    ELSIF EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = 'User_username_key' AND n.nspname = 'public'
    ) THEN
        DROP INDEX public."User_username_key";
    END IF;
END $$;

-- 3. Create case-insensitive, whitespace-trimmed unique functional index
CREATE UNIQUE INDEX IF NOT EXISTS "uq_user_username_lower_trim"
ON public."User" (LOWER(TRIM("username")));

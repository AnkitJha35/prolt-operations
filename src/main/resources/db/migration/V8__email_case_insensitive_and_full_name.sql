-- Migration: V8__email_case_insensitive_and_full_name.sql
-- Description: Enforce case-insensitive and whitespace-insensitive email uniqueness and add fullName column

-- 1. Preflight safety check: abort migration if duplicate normalized emails exist
DO $$
DECLARE
    dup_count INT;
BEGIN
    SELECT COUNT(*) INTO dup_count
    FROM (
        SELECT LOWER(TRIM("email"))
        FROM "public"."User"
        WHERE "email" IS NOT NULL
        GROUP BY LOWER(TRIM("email"))
        HAVING COUNT(*) > 1
    ) d;

    IF dup_count > 0 THEN
        RAISE EXCEPTION 'Flyway migration V8 aborted: Found % duplicate normalized emails in public."User". Resolve duplicates before migrating.', dup_count;
    END IF;
END $$;

-- 2. Safely drop legacy case-sensitive unique index/constraint on email if it exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public."User"'::regclass AND conname = 'User_email_key'
    ) THEN
        ALTER TABLE public."User" DROP CONSTRAINT "User_email_key";
    ELSIF EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = 'User_email_key' AND n.nspname = 'public'
    ) THEN
        DROP INDEX public."User_email_key";
    END IF;
END $$;

-- 3. Create case-insensitive, whitespace-trimmed unique functional index
CREATE UNIQUE INDEX IF NOT EXISTS "uq_user_email_lower_trim"
ON public."User" (LOWER(TRIM("email")));

-- 4. Add fullName column as nullable (Stage 1 of safe staged rollout)
ALTER TABLE public."User"
ADD COLUMN IF NOT EXISTS "fullName" VARCHAR(100);

-- ==============================================================================
-- Flyway Migration V13: Remove passwordHash column from public."User"
-- Part of Transition to OTP-Only Authentication
-- ==============================================================================

-- Preflight Assertion 1: Ensure public."User" table exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'User'
    ) THEN
        RAISE EXCEPTION 'Flyway V13 Preflight Failed: Table public."User" does not exist';
    END IF;
END $$;

-- Preflight Assertion 2: Verify all existing user accounts have non-blank email addresses
DO $$
DECLARE
    invalid_user_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_user_count
    FROM public."User"
    WHERE email IS NULL OR TRIM(email) = '';

    IF invalid_user_count > 0 THEN
        RAISE EXCEPTION 'Flyway V13 Preflight Failed: % user account(s) have missing or blank email addresses. All users must have valid emails for OTP authentication before dropping passwordHash.', invalid_user_count;
    END IF;
END $$;

-- Migration: Drop passwordHash column from public."User"
ALTER TABLE public."User" DROP COLUMN IF EXISTS "passwordHash";

-- Postflight Assertion: Verify column was successfully dropped
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'User'
          AND column_name = 'passwordHash'
    ) THEN
        RAISE EXCEPTION 'Flyway V13 Postflight Failed: Column public."User"."passwordHash" was not dropped';
    END IF;
END $$;

COMMENT ON TABLE public."User" IS 'System user accounts authenticated strictly via email OTP';

-- Migration: V10__enforce_user_full_name_not_null.sql
-- Description: Enforce NOT NULL constraint on public."User"."fullName"

DO $$
DECLARE
    v_invalid_count INT;
    v_is_nullable VARCHAR;
BEGIN
    -- 1. Preflight safety check: abort if any NULL, empty, or whitespace-only fullName exists
    SELECT COUNT(*) INTO v_invalid_count
    FROM public."User"
    WHERE "fullName" IS NULL OR TRIM("fullName") = '';

    IF v_invalid_count > 0 THEN
        RAISE EXCEPTION 'Flyway migration V10 aborted: Found % user record(s) with NULL, empty, or whitespace-only fullName in public."User". All users must have a valid fullName before enforcing NOT NULL.', v_invalid_count;
    END IF;

    -- 2. Enforce NOT NULL constraint on public."User"."fullName"
    ALTER TABLE public."User" ALTER COLUMN "fullName" SET NOT NULL;

    -- 3. Postflight verification: Confirm column constraint in information_schema
    SELECT is_nullable INTO v_is_nullable
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'User'
      AND column_name = 'fullName';

    IF v_is_nullable <> 'NO' THEN
        RAISE EXCEPTION 'Flyway migration V10 postflight failed: public."User"."fullName" is_nullable is %, expected NO', v_is_nullable;
    END IF;

    RAISE NOTICE 'Flyway migration V10 completed successfully: NOT NULL constraint enforced on public."User"."fullName".';
END $$;

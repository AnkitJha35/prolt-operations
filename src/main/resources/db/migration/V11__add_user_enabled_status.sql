-- Migration: V11__add_user_enabled_status.sql
-- Description: Add enabled status column to public."User" with DEFAULT TRUE and NOT NULL

DO $$
DECLARE
    v_col_exists BOOLEAN;
    v_is_nullable VARCHAR;
    v_column_default TEXT;
    v_invalid_count INT;
BEGIN
    -- 1. Check if column already exists
    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'User'
          AND column_name = 'enabled'
    ) INTO v_col_exists;

    -- 2. Add column if not exists, or ensure default and NOT NULL if it does
    IF NOT v_col_exists THEN
        ALTER TABLE public."User" ADD COLUMN "enabled" BOOLEAN NOT NULL DEFAULT TRUE;
    ELSE
        ALTER TABLE public."User" ALTER COLUMN "enabled" SET DEFAULT TRUE;
        UPDATE public."User" SET "enabled" = TRUE WHERE "enabled" IS NULL;
        ALTER TABLE public."User" ALTER COLUMN "enabled" SET NOT NULL;
    END IF;

    -- 3. Postflight verification: Confirm column existence, NOT NULL, and DEFAULT
    SELECT is_nullable, column_default INTO v_is_nullable, v_column_default
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'User'
      AND column_name = 'enabled';

    IF v_is_nullable <> 'NO' THEN
        RAISE EXCEPTION 'Flyway migration V11 postflight failed: public."User"."enabled" is_nullable is %, expected NO', v_is_nullable;
    END IF;

    IF v_column_default NOT LIKE '%true%' THEN
        RAISE EXCEPTION 'Flyway migration V11 postflight failed: public."User"."enabled" column_default is %, expected true', v_column_default;
    END IF;

    -- Confirm all existing users have enabled = true
    SELECT COUNT(*) INTO v_invalid_count
    FROM public."User"
    WHERE "enabled" IS NOT TRUE;

    IF v_invalid_count > 0 THEN
        RAISE EXCEPTION 'Flyway migration V11 postflight failed: Found % user record(s) where enabled is not TRUE', v_invalid_count;
    END IF;

    RAISE NOTICE 'Flyway migration V11 completed successfully: enabled column added to public."User" with DEFAULT TRUE and NOT NULL.';
END $$;

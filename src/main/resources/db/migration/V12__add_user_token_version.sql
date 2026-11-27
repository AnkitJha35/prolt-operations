-- Migration: V12__add_user_token_version.sql
-- Description: Add tokenVersion column to public."User" with DEFAULT 0 and NOT NULL

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
          AND column_name = 'tokenVersion'
    ) INTO v_col_exists;

    -- 2. Add column if not exists, or ensure default and NOT NULL if it does
    IF NOT v_col_exists THEN
        ALTER TABLE public."User" ADD COLUMN "tokenVersion" INTEGER NOT NULL DEFAULT 0;
    ELSE
        ALTER TABLE public."User" ALTER COLUMN "tokenVersion" SET DEFAULT 0;
        UPDATE public."User" SET "tokenVersion" = 0 WHERE "tokenVersion" IS NULL;
        ALTER TABLE public."User" ALTER COLUMN "tokenVersion" SET NOT NULL;
    END IF;

    -- 3. Postflight verification: Confirm column existence, NOT NULL, and DEFAULT
    SELECT is_nullable, column_default INTO v_is_nullable, v_column_default
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'User'
      AND column_name = 'tokenVersion';

    IF v_is_nullable <> 'NO' THEN
        RAISE EXCEPTION 'Flyway migration V12 postflight failed: public."User"."tokenVersion" is_nullable is %, expected NO', v_is_nullable;
    END IF;

    IF v_column_default NOT LIKE '%0%' THEN
        RAISE EXCEPTION 'Flyway migration V12 postflight failed: public."User"."tokenVersion" column_default is %, expected 0', v_column_default;
    END IF;

    -- Confirm all existing users have a valid tokenVersion >= 0
    SELECT COUNT(*) INTO v_invalid_count
    FROM public."User"
    WHERE "tokenVersion" IS NULL OR "tokenVersion" < 0;

    IF v_invalid_count > 0 THEN
        RAISE EXCEPTION 'Flyway migration V12 postflight failed: Found % user record(s) where tokenVersion is NULL or negative', v_invalid_count;
    END IF;

    RAISE NOTICE 'Flyway migration V12 completed successfully: tokenVersion column added to public."User" with DEFAULT 0 and NOT NULL.';
END $$;

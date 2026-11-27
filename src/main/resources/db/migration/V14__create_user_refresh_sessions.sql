-- ==============================================================================
-- Flyway Migration V14: Create User Refresh Sessions Table
-- Milestone V14: Secure JWT Access Token & Refresh Token Architecture
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Preflight Validation Assertion
-- Verify that public."User" table exists before creating foreign key
-- ------------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'User'
    ) THEN
        RAISE EXCEPTION 'Flyway V14 Preflight Failed: Required table public.User does not exist.';
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- 2. Create user_refresh_sessions Table
-- Stores SHA-256 hashes of opaque refresh tokens with family tracking and audit fields
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public."user_refresh_sessions" (
    "id" VARCHAR(36) NOT NULL,
    "userId" VARCHAR(255) NOT NULL,
    "tokenHash" VARCHAR(64) NOT NULL,
    "tokenFamilyId" VARCHAR(36) NOT NULL,
    "tokenVersion" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    "expiresAt" TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    "lastUsedAt" TIMESTAMP WITHOUT TIME ZONE,
    "revokedAt" TIMESTAMP WITHOUT TIME ZONE,
    "replacedById" VARCHAR(36),
    "createdIp" VARCHAR(45),
    "userAgent" VARCHAR(500),
    CONSTRAINT pk_user_refresh_sessions PRIMARY KEY ("id"),
    CONSTRAINT fk_user_refresh_sessions_user FOREIGN KEY ("userId") REFERENCES public."User" ("id") ON DELETE CASCADE,
    CONSTRAINT uq_user_refresh_sessions_token_hash UNIQUE ("tokenHash")
);

-- ------------------------------------------------------------------------------
-- 3. Create Optimization Indexes
-- Supports rapid lookup by user, token family (for reuse detection), and expiration cleanup
-- ------------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_user_refresh_sessions_user_id
    ON public."user_refresh_sessions" ("userId");

CREATE INDEX IF NOT EXISTS idx_user_refresh_sessions_family_id
    ON public."user_refresh_sessions" ("tokenFamilyId");

CREATE INDEX IF NOT EXISTS idx_user_refresh_sessions_expires_at
    ON public."user_refresh_sessions" ("expiresAt");

-- ------------------------------------------------------------------------------
-- 4. Postflight Validation Assertion
-- ------------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'user_refresh_sessions'
    ) THEN
        RAISE EXCEPTION 'Flyway V14 Postflight Failed: Table public.user_refresh_sessions was not created.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'user_refresh_sessions'
          AND constraint_name = 'uq_user_refresh_sessions_token_hash'
    ) THEN
        RAISE EXCEPTION 'Flyway V14 Postflight Failed: Unique constraint uq_user_refresh_sessions_token_hash does not exist.';
    END IF;
END $$;

-- Migration V6: Create dsm_charges if missing
-- Canonical source: IEX-Dashboard/backend/prisma/schema.prisma lines 1207-1217
-- Verification document: docs/30-blocker-verification-and-patch-plan.md

CREATE TABLE IF NOT EXISTS "public"."dsm_charges" (
    "id" SERIAL NOT NULL,
    "state" VARCHAR(100) NOT NULL,
    "month" INTEGER NOT NULL,
    "dsm_charges_rs_per_kwh" DECIMAL(12,6),
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "dsm_charges_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX IF NOT EXISTS "uq_dsm_charges_state_month" ON "public"."dsm_charges"("state", "month");

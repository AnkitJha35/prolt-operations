# Deferred Product and Data Decisions

This document records decisions that require an owner or approved source data. Until a decision is made, the Node application is the behavioral authority and the Java implementation must not invent substitute data, change destructive behavior, or silently select a new operational process.

## D-01: Initial master-data loading

**Node behavior**

- `IEX-Dashboard/backend/prisma/seed.ts` clears `stateCharges` and imports a locally available CSV.
- `IEX-Dashboard/backend/src/scripts/seed_tariff.ts` and `seed_tariff_new.ts` clear `stateTariff` and import locally available spreadsheet data.
- The source CSV/XLSX files and their original macOS paths are not available in this repository.
- The application also exposes JSON APIs for normal management: `POST /api/resource-center/:resourceType`, `POST /api/resource-center/:resourceType/bulk`, `PUT /api/resource-center/:resourceType/:id`, and `DELETE /api/resource-center/:resourceType/:id`.

**Current Java parity position**

- Java exposes the same Resource Center CRUD and JSON bulk-import route shapes for master data, including `state-charges` and `state-tariff`.
- Java exposes `GET` and `POST /api/database/city-state`, matching Node's city/state API shape.
- The Flyway seed migrations deliberately contain no invented master data because the actual Node source files are absent.

**Decision required later**

Choose and approve the source of the first complete dataset for each environment. Possible sources include an approved database export or the original source spreadsheets. If the destructive Node scripts are to be ported, provide the exact files, expected runtime location, and authorization to delete/replace existing master data.

**Planning status**

Deferred to the next development plan. The configured database has been inspected read-only and contains current master-data rows, but no export, import, deletion, or production change is authorized in this phase.

## D-02: Master-data operational workflow

**Question**

After initial loading, should operators use the Resource Center JSON bulk API directly, use an application UI that converts spreadsheets to JSON, or use a controlled import tool?

**Current behavior**

The backend bulk endpoint accepts a JSON array; it does not accept a CSV/XLSX multipart upload for Resource Center master data. Any spreadsheet-to-JSON conversion is a separate client or operator responsibility.

**Decision required later**

Define the supported operator workflow, access controls, validation/review process, rollback process, and audit requirements before adding a new file-upload feature.

## D-03: Resource Center field-schema authority [RESOLVED]

**Status**: Resolved via Option A (Canonical Schema Alignment) in `docs/20-resource-center-schema-audit.md`.

**Resolution Summary**:
- The authoritative database contract is the existing PostgreSQL/Prisma/Flyway schema.
- Comprehensive source-code audit proved that the discrepancy in `state-tariff` was an isolated validator mismatch in Node, while the Prisma schema, PostgreSQL database, React frontend (`ResourceCenterTariffModal.tsx`), and calculation engines all bind to the canonical columns (`consumerCategory`, `energyRate`, `supplyVoltage`, `todChargePercent`, etc.).
- Complete field mappings and CRUD parity implemented and verified across all 9 Resource Center tables in `ResourceCenterIntegrationTest.java` (14 test cases, RC-01..RC-14).


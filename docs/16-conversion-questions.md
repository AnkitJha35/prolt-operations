# Conversion Questions & Parity Decisions Log

This document records any ambiguities, discrepancies, edge cases, or known Node.js quirks encountered during conversion, along with the parity-preserving decision made.

| # | Topic / Area | Node Source Reference | Question / Ambiguity | Parity Decision Made & Rationale |
|---|---|---|---|---|
| 1 | Package layout vs Document 14 | `mdm-adaptor` layout vs Doc 14 `com.prolt.iex` | Doc 14 suggested package `com.prolt.iex.*` with domain-driven subpackages, but reference `mdm-adaptor` and project pom specify `com.sense.prolt_operations` and layered structure `config, controller, dto, entity, repository, exception, runner, scheduler, security, service, util`. | Follow reference conventions: base package `com.sense.prolt_operations` with layered packages. Recorded updated mapping table in conversion log. |
| 2 | Error Response Body Format | `backend/src/middleware/errorHandler.ts` vs `mdm-adaptor` | `mdm-adaptor`'s `ErrorResponse` has timestamp, status, error, message, path; but Node returns `{ success: false, message: "..." }`. | Match Node format exactly: `{ "success": false, "message": "..." }`. |
| 3 | Admin Role Hierarchy | `backend/src/middleware/admin.middleware.ts` | `adminMiddleware` explicitly checks `req.user.role !== 'ADMIN'` without checking `SUPER_ADMIN`. | Ported faithfully: only `ADMIN` is permitted on routes guarded solely by `adminMiddleware`, matching Node parity. |


# Conversion Questions & Parity Decisions Log

This document records any ambiguities, discrepancies, edge cases, or known Node.js quirks encountered during conversion, along with the parity-preserving decision made.

| # | Topic / Area | Node Source Reference | Question / Ambiguity | Parity Decision Made & Rationale |
|---|---|---|---|---|
| 1 | Package layout vs Document 14 | `mdm-adaptor` layout vs Doc 14 `com.prolt.iex` | Doc 14 suggested package `com.prolt.iex.*` with domain-driven subpackages, but reference `mdm-adaptor` and project pom specify `com.sense.prolt_operations` and layered structure `config, controller, dto, entity, repository, exception, runner, scheduler, security, service, util`. | Follow reference conventions: base package `com.sense.prolt_operations` with layered packages. Recorded updated mapping table in conversion log. |
| 2 | Error Response Body Format | `backend/src/middleware/errorHandler.ts` vs `mdm-adaptor` | `mdm-adaptor`'s `ErrorResponse` has timestamp, status, error, message, path; but Node returns `{ success: false, message: "..." }`. | Match Node format exactly: `{ "success": false, "message": "..." }`. |
| 3 | Admin Role Hierarchy | `backend/src/middleware/admin.middleware.ts` | `adminMiddleware` explicitly checks `req.user.role !== 'ADMIN'` without checking `SUPER_ADMIN`. | Ported faithfully: only `ADMIN` is permitted on routes guarded solely by `adminMiddleware`, matching Node parity. |
| 4 | Superadmin OTP Backdoor | `backend/src/modules/auth/otp.service.ts` | Hardcoded bypass for `super.admin@probus.io` with fixed OTP `749261`. | Preserved as-is to ensure exact 1:1 parity with Node behavior and automated end-to-end verification. |
| 5 | In-Memory OTP Store vs Redis | `backend/src/modules/auth/otp.service.ts` vs Doc 06 | Node stored OTP in a local Map; Doc 06 requested Redis. | Implemented resilient dual-layer: checks/writes to Redis if connected, falls back gracefully to `ConcurrentHashMap` in dev/test when Redis is unreachable. |
| 6 | `/api/auth/me` Response Property | `backend/src/modules/auth/auth.controller.ts:L51` vs Doc 02 | Node code returns `{ success: true, user }` while Doc 02 / Swagger states `{ success: true, data }`. | Provided both `user` and `data` fields in response payload, ensuring full backwards compatibility with all clients. |
| 7 | Dual Route Aliases for OTP | `backend/src/modules/auth/auth.routes.ts` vs Doc 12 | Node defined `/send-otp` and `/verify-otp`, while Doc 12 scenario AUTH-02/03 referenced `/otp/generate` and `/otp/verify`. | Supported both URI patterns in `AuthController` (`@PostMapping({"/send-otp", "/otp/generate"})` and `{"/verify-otp", "/otp/verify"}`). |



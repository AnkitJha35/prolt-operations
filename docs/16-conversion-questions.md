# Conversion Questions & Parity Decisions Log

This document records any ambiguities, discrepancies, edge cases, or known Node.js quirks encountered during conversion, along with the parity-preserving decision made.

| # | Topic / Area | Node Source Reference | Question / Ambiguity | Parity Decision Made & Rationale |
|---|---|---|---|---|
| 1 | Package layout vs Document 14 | `mdm-adaptor` layout vs Doc 14 `com.prolt.iex` | Doc 14 suggested package `com.prolt.iex.*` with domain-driven subpackages, but reference `mdm-adaptor` and project pom specify `com.sense.prolt_operations` and layered structure `config, controller, dto, entity, repository, exception, runner, scheduler, security, service, util`. | Follow reference conventions: base package `com.sense.prolt_operations` with layered packages. Recorded updated mapping table in conversion log. |
| 2 | Error Response Body Format | `backend/src/middleware/errorHandler.ts` vs `mdm-adaptor` | `mdm-adaptor`'s `ErrorResponse` has timestamp, status, error, message, path; but Node returns `{ success: false, message: "..." }`. | Match Node format exactly: `{ "success": false, "message": "..." }`. |
| 3 | Admin Role Hierarchy | `backend/src/middleware/admin.middleware.ts` | `adminMiddleware` explicitly checks `req.user.role !== 'ADMIN'` without checking `SUPER_ADMIN`. | Ported faithfully: only `ADMIN` is permitted on routes guarded solely by `adminMiddleware`, matching Node parity. |
| 4 | Superadmin OTP Backdoor | `backend/src/modules/auth/otp.service.ts` | Hardcoded bypass for `super.admin@probus.io` with fixed OTP `749261`. | Preserved for test/dev via `app.auth.superadmin-otp-bypass.enabled=true` with property-based OTP value (`app.auth.superadmin-otp-bypass.otp-value`). Disabled (`false`) in production profile. |
| 5 | In-Memory OTP Store vs Redis | `backend/src/modules/auth/otp.service.ts` vs Doc 06 | Node stored OTP in a local Map; Doc 06 requested Redis. | In-memory fallback allowed only in `dev` and `test` profiles (`app.auth.otp.allow-memory-fallback=true`). In `prod` (`allow-memory-fallback=false`), if Redis is unavailable, logs ERROR and fails request with 500. |
| 6 | `/api/auth/me` Response Property | `backend/src/modules/auth/auth.controller.ts:L51` vs Doc 02 | Node code returns `{ success: true, user }` while Doc 02 / Swagger states `{ success: true, data }`. | Provided both `user` and `data` fields in response payload, ensuring full backwards compatibility with all clients. |
| 7 | Dual Route Aliases for OTP | `backend/src/modules/auth/auth.routes.ts` vs Doc 12 | Node defined `/send-otp` and `/verify-otp`, while Doc 12 scenario AUTH-02/03 referenced `/otp/generate` and `/otp/verify`. | Decision applied: Removed `/otp/generate` and `/otp/verify`. Kept only canonical `/send-otp` and `/verify-otp`. Corrected 12-test-baseline.md scenarios. |
| 8 | Singular Route Paths (`/api/proposal`, `/api/dataset`, `/api/api-log`) | `backend/src/app.ts` vs Docs 02/12/14/15 & Swagger | Singular paths appear in IEX-Dashboard docs 02/12/14/15 and Swagger comments, but Node never served them (Node only mounts plural `/api/proposals`, `/api/datasets`, and `/api/logs`). | Removed invented singular routes from controllers, security config, and tests to match Node authority exactly. Kept real double mount `/api/forecast` and `/forecast`. |

## Known Node Quirks

### 1. SUPER_ADMIN Exclusion on `adminMiddleware` Routes
- **Node Source Reference**: `backend/src/middleware/admin.middleware.ts`
  ```typescript
  export const adminMiddleware = (req: Request, res: Response, next: NextFunction) => {
    if (!req.user || req.user.role !== 'ADMIN') {
      return res.status(403).json({ success: false, message: 'Forbidden: Admin access required' });
    }
    next();
  };
  ```
- **Quirk Description**: Standard hierarchical RBAC usually implies `SUPER_ADMIN` possesses a superset of `ADMIN` capabilities. However, in the Node.js implementation, `adminMiddleware` tests strictly for exact string equality with `'ADMIN'`, therefore returning `403 Forbidden` if a user with role `SUPER_ADMIN` attempts to access routes guarded only by `adminMiddleware`. Conversely, `superAdminMiddleware` tests strictly for `'SUPER_ADMIN'`.
- **Resolution**: While `adminMiddleware` is defined in Node, it is used by no route in the application (`grep` across all route files shows 0 usages of `adminMiddleware`). Thus, the earlier "ADMIN only" quirk has no runtime effect on any endpoint. Only `superAdminMiddleware` is actually attached to routes (`/api/users/**`), requiring `SUPER_ADMIN`.





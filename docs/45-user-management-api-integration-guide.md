# User Management API Integration Guide

**Document Version:** 1.0.0  
**Target Audience:** Frontend / UI Engineering Team  
**Backend Repository:** `prolt-operations` (Spring Boot 3.3.4 / Java 21)  
**Security Standard:** Spring Security 6, Stateless JWT with Token Versioning, Role-Based Access Control (RBAC)  
**Status:** Verified against Backend Implementation

---

## 1. Executive Summary & Purpose

This document provides a comprehensive, frontend-ready integration guide for the **User Identity & Management Module** of `prolt-operations`. It documents the exact contracts, schemas, authentication mechanisms, authorization rules, and error behaviors as implemented in the backend codebase.

The frontend engineering team can use this document as a complete specification to build, integrate, and verify the administrative User Management interface without inspecting backend source code.

---

## 2. Global Architecture & API Conventions

### 2.1 Base URLs & Environments

| Environment | Base URL | Notes |
| :--- | :--- | :--- |
| **Local Development** | `http://localhost:8080` (or `http://localhost:5001/api` via proxy) | Default Spring Boot dev server |
| **Staging** | `https://staging.domain.com/api` (or `:5001/api`) | Pre-production testing environment |
| **Production** | `https://app.domain.com/api` (or `:5002/api`) | Live production cluster |

*Note: All endpoints documented below are prefixed with `/api`.*

### 2.2 Content Type & Character Encoding

- **Request Payload:** `Content-Type: application/json`
- **Response Payload:** `Content-Type: application/json;charset=UTF-8`
- **Character Encoding:** UTF-8

### 2.3 Authentication Header

Every protected request must provide a valid JSON Web Token (JWT) in the standard HTTP `Authorization` header:

```http
Authorization: Bearer <jwt_token>
```

Requests without this header or with an invalid/expired token will receive `401 Unauthorized`.

### 2.4 Uniform Response Envelope

Successful backend responses follow a standardized envelope:

#### Object / Entity Envelope
```json
{
  "success": true,
  "data": { ... }
}
```

#### List / Collection Envelope
```json
{
  "success": true,
  "data": [ ... ]
}
```

#### Operational Message Envelope
```json
{
  "success": true,
  "message": "User deleted successfully"
}
```

#### Message + Data Envelope (e.g. Session Revocation)
```json
{
  "success": true,
  "message": "All active sessions for user successfully revoked",
  "data": { ... }
}
```

### 2.5 Uniform Error Response Schema

When an operation fails (validation, business error, missing resource, authentication, or authorization failure), the backend returns:

```json
{
  "success": false,
  "message": "Specific error description explaining the failure"
}
```

#### Key Fields:
- `success` (`boolean`): Always `false` on error.
- `message` (`string`): Human-readable error message. Frontend applications should display this message directly via toast notifications or alert banners.

### 2.6 Naming Conventions & Timestamps

- **Field Naming:** `camelCase` (e.g., `fullName`, `hiddenModules`, `readOnlyModules`, `tokenVersion`, `createdAt`).
- **Timestamps:** ISO-8601 strings in local datetime representation (e.g., `"2026-11-02T15:30:00"`).

---

## 3. Security, Roles & Session Architecture

### 3.1 Role Hierarchy & Permissions

The application implements three distinct roles (`Role` enum):

| Role | Hierarchy / Description | Access to `/api/users/**` | Access to Business Modules |
| :--- | :--- | :---: | :--- |
| `SUPER_ADMIN` | Root administrator | **Full Access** | Unrestricted access across all modules |
| `ADMIN` | Operational administrator | **Blocked (403 Forbidden)** | Access configured via module lists |
| `CLIENT` | Standard consumer / client | **Blocked (403 Forbidden)** | Access configured via module lists |

> [!IMPORTANT]
> All endpoints under `/api/users/**` are annotated with `@PreAuthorize("hasRole('SUPER_ADMIN')")`.
> If an authenticated user with role `ADMIN` or `CLIENT` attempts to access any `/api/users` endpoint, the backend immediately returns:
> ```json
> {
>   "success": false,
>   "message": "Forbidden: Super Admin access required"
> }
> ```
> Frontend navigation menus must hide the "User Management" link for users who do not have `role === 'SUPER_ADMIN'`.

### 3.2 Granular Module-Level Permissions

Access to application modules is governed by two string lists stored on each user account:

1. `hiddenModules` (`string[]`): Modules completely hidden from the user's navigation and UI.
2. `readOnlyModules` (`string[]`): Modules the user can view but cannot modify/export.

#### Standard System Module IDs:
- `database` — Demand & Generation Data
- `markets` — IEX Market
- `resource-center` — Resource Center
- `market-operations` — Market Operations
- `savings-calculator` — Savings Calculator
- `savings-calculator-new` — Savings Calculator (New)
- `forecast` — Forecast Analytics
- `market-recommendation` — Market Recommendation
- `api-logs` — API Logs
- `user-management` — User Management
- `trader-performance` — Trader Performance
- `trader-performance-daily` — Trader Performance Daily

### 3.3 Account Status (`enabled`)

Each user account has an `enabled` boolean flag:
- `enabled = true`: The user can log in and execute authorized API calls.
- `enabled = false`: The account is locked.

#### Security Semantics of Account Disablement:
1. When disabled, the user's active session is invalidated immediately (by incrementing `tokenVersion` and updating the security state cache).
2. Any subsequent request by the disabled user using an already-issued JWT is rejected with:
   ```json
   {
     "success": false,
     "message": "Account is disabled. Please contact administrator."
   }
   ```
3. Login attempts for disabled accounts via `/api/auth/login` are rejected with `401 Unauthorized`:
   ```json
   {
     "success": false,
     "message": "Account is disabled. Please contact administrator."
   }
   ```

### 3.4 JWT Token Versioning & Session Revocation (`tokenVersion`)

To overcome the stateless vulnerability of JWTs (where a token remains valid until expiration even after account revocation or password changes), the backend implements a stateful **Token Versioning** mechanism:

- Each user has an integer `tokenVersion` in the database (default `0`).
- Every JWT issued by `/api/auth/login` or `/api/auth/verify-otp` embeds a `tokenVersion` claim.
- On every protected request, `JwtAuthenticationFilter` validates that the token's `tokenVersion` claim matches the user's current security state.
- **Triggers that increment `tokenVersion` (invalidating existing tokens):**
  1. **Disabling Account:** `PATCH /api/users/{id}/status` with `{"enabled": false}`.
  2. **Explicit Session Revocation:** `POST /api/users/{id}/revoke-sessions`.
  3. **Password Change:** `PUT /api/users/{id}` with a non-blank `password`.
- **Note on Re-enabling:** When an account is re-enabled (`{"enabled": true}`), `tokenVersion` is **not** rolled back. Previously issued tokens remain revoked; the user must log in again to acquire a fresh JWT.

---

## 4. Complete API Reference

### 4.1 User Administration (`/api/users/**`)
*All endpoints in this section require `Authorization: Bearer <token>` and `SUPER_ADMIN` role.*

---

#### 4.1.1 List All Users

Retrieves all user records ordered by creation date descending (`createdAt DESC`).

- **Method / Path:** `GET /api/users`
- **Authorization:** `SUPER_ADMIN`
- **Request Parameters / Body:** None

##### Success Response (`200 OK`)
```json
{
  "success": true,
  "data": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "username": "johndoe",
      "email": "johndoe@example.com",
      "fullName": "John Doe",
      "role": "ADMIN",
      "enabled": true,
      "hiddenModules": ["api-logs"],
      "readOnlyModules": ["resource-center"],
      "createdAt": "2026-11-01T10:15:30"
    },
    {
      "id": "e7b0e122-3849-41bc-9387-a2f0709f8c12",
      "username": "clientuser",
      "email": "client@example.com",
      "fullName": "Jane Client",
      "role": "CLIENT",
      "enabled": false,
      "hiddenModules": ["database", "markets", "user-management"],
      "readOnlyModules": [],
      "createdAt": "2026-10-28T14:20:00"
    }
  ]
}
```

##### Response Fields (`UserDTO`):
| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | `string` | Unique user UUID |
| `username` | `string` | Immutable login username |
| `email` | `string` | Normalized unique email address |
| `fullName` | `string` | User's full display name (nullable for legacy records, populated for all new/updated users) |
| `role` | `string` | `"SUPER_ADMIN"`, `"ADMIN"`, or `"CLIENT"` |
| `enabled` | `boolean` | Account active state (`true` = active, `false` = disabled) |
| `hiddenModules` | `string[]` | Modules hidden from navigation |
| `readOnlyModules` | `string[]` | Modules restricted to read-only mode |
| `createdAt` | `string` | ISO-8601 timestamp of user creation |

---

#### 4.1.2 Create User

Creates a new user account. Only `SUPER_ADMIN` can execute this.

- **Method / Path:** `POST /api/users`
- **Authorization:** `SUPER_ADMIN`
- **Status on Success:** `201 Created`

##### Request Body (`CreateUserDTO`):
```json
{
  "username": "analyst_ops",
  "email": "analyst.ops@example.com",
  "fullName": "Operations Analyst",
  "password": "TemporaryPassword123!",
  "role": "ADMIN",
  "hiddenModules": ["api-logs"],
  "readOnlyModules": ["resource-center"]
}
```

##### Field Specifications & Validation Rules:
| Field | Type | Required | Rules & Constraints |
| :--- | :--- | :---: | :--- |
| `username` | `string` | **Yes** | Trimmed. Case-insensitive uniqueness enforced across the database. Once created, **immutable**. |
| `email` | `string` | **Yes** | Trimmed and normalized to lowercase. Must be unique. |
| `fullName` | `string` | **Yes** | Trimmed. Cannot be blank. Maximum length: 100 characters. |
| `password` | `string` | No | Optional. If omitted or blank, backend auto-generates a secure random 32-character string. |
| `role` | `string` | **Yes** | Case-insensitive. Must be either `"ADMIN"` or `"CLIENT"`. Creating `"SUPER_ADMIN"` is prohibited. |
| `hiddenModules` | `string[]` | No | Array of module strings. Defaults to `[]` if omitted. |
| `readOnlyModules` | `string[]` | No | Array of module strings. Defaults to `[]` if omitted. |

##### Success Response (`201 Created`):
```json
{
  "success": true,
  "data": {
    "id": "8b9e59d2-9cf5-4e01-9a74-9b2f63cb32a1",
    "username": "analyst_ops",
    "email": "analyst.ops@example.com",
    "fullName": "Operations Analyst",
    "role": "ADMIN",
    "enabled": true,
    "hiddenModules": ["api-logs"],
    "readOnlyModules": ["resource-center"],
    "createdAt": "2026-11-02T16:00:00"
  }
}
```

##### Error Responses:
- `400 Bad Request` — `"Username is required"`
- `400 Bad Request` — `"Email is required"`
- `400 Bad Request` — `"Full name is required"`
- `400 Bad Request` — `"Full name must not exceed 100 characters"`
- `400 Bad Request` — `"Valid role is required (ADMIN or CLIENT)"`
- `400 Bad Request` — `"Username or email already exists"` (duplicate username or email)

---

#### 4.1.3 Update User

Updates profile, role, module rights, or password for an existing user.

- **Method / Path:** `PUT /api/users/{id}`
- **Authorization:** `SUPER_ADMIN`
- **Status on Success:** `200 OK`

> [!NOTE]
> - `username` is **immutable** and cannot be changed. It is intentionally omitted from `UpdateUserDTO`.
> - `enabled` cannot be modified via `PUT`. Use `PATCH /api/users/{id}/status` to toggle active status.

##### Request Body (`UpdateUserDTO`):
```json
{
  "email": "updated.email@example.com",
  "fullName": "Updated Name",
  "role": "CLIENT",
  "password": "NewSecretPassword456!",
  "hiddenModules": ["database", "markets"],
  "readOnlyModules": ["forecast"]
}
```

##### Field Specifications:
| Field | Type | Required | Rules & Constraints |
| :--- | :--- | :---: | :--- |
| `email` | `string` | No | If provided, must not be blank. Normalized to lowercase. Checked for uniqueness against other users. |
| `fullName` | `string` | No | If provided, must not be blank and must not exceed 100 characters. |
| `role` | `string` | No | If provided, must be `"ADMIN"` or `"CLIENT"`. |
| `password` | `string` | No | If provided and non-blank, bcrypt-hashed, updates database password, and **increments `tokenVersion`** to revoke existing sessions. |
| `hiddenModules` | `string[]` | No | If provided, replaces existing hidden modules list. |
| `readOnlyModules` | `string[]` | No | If provided, replaces existing read-only modules list. |

##### Success Response (`200 OK`):
```json
{
  "success": true,
  "data": {
    "id": "8b9e59d2-9cf5-4e01-9a74-9b2f63cb32a1",
    "username": "analyst_ops",
    "email": "updated.email@example.com",
    "fullName": "Updated Name",
    "role": "CLIENT",
    "enabled": true,
    "hiddenModules": ["database", "markets"],
    "readOnlyModules": ["forecast"],
    "createdAt": "2026-11-02T16:00:00"
  }
}
```

##### Error Responses:
- `404 Not Found` — `"User not found"`
- `400 Bad Request` — `"Email must be a valid string"`
- `400 Bad Request` — `"Full name must be a valid string"`
- `400 Bad Request` — `"Full name must not exceed 100 characters"`
- `400 Bad Request` — `"Valid role is required (ADMIN or CLIENT)"`
- `400 Bad Request` — `"Username or email already exists"` (email collision with another account)

---

#### 4.1.4 Update User Status (Enable / Disable)

Enables or disables a user account. Disabling an account immediately revokes all active JWT sessions.

- **Method / Path:** `PATCH /api/users/{id}/status`
- **Authorization:** `SUPER_ADMIN`
- **Status on Success:** `200 OK`

##### Request Body (`UpdateUserStatusDTO`):
```json
{
  "enabled": false
}
```

##### Validation & Business Rules:
1. `enabled` is required (`@NotNull(message = "enabled field is required")`).
2. **Self-Disable Guard:** A Super Administrator cannot disable their own account.
3. **Last Active Super Admin Guard:** The system will reject disabling the last active `SUPER_ADMIN` account to prevent total administrative lockout.

##### Success Response (`200 OK`):
```json
{
  "success": true,
  "data": {
    "id": "8b9e59d2-9cf5-4e01-9a74-9b2f63cb32a1",
    "username": "analyst_ops",
    "email": "updated.email@example.com",
    "fullName": "Updated Name",
    "role": "CLIENT",
    "enabled": false,
    "hiddenModules": ["database", "markets"],
    "readOnlyModules": ["forecast"],
    "createdAt": "2026-11-02T16:00:00"
  }
}
```

##### Error Responses:
- `400 Bad Request` — `"enabled field is required"`
- `400 Bad Request` — `"You cannot disable your own account"`
- `400 Bad Request` — `"Cannot disable the last active Super Administrator account"`
- `404 Not Found` — `"User not found"`

---

#### 4.1.5 Revoke User Sessions

Invalidates all currently active JWT tokens for a specific user without disabling the account or changing the password.

- **Method / Path:** `POST /api/users/{id}/revoke-sessions`
- **Authorization:** `SUPER_ADMIN`
- **Status on Success:** `200 OK`
- **Request Body:** None

##### Business Safeguards:
- **Self-Revocation Guard:** A Super Administrator cannot revoke their own active session (`400 Bad Request: "You cannot revoke your own active session"`).

##### Success Response (`200 OK`):
```json
{
  "success": true,
  "message": "All active sessions for user successfully revoked",
  "data": {
    "id": "8b9e59d2-9cf5-4e01-9a74-9b2f63cb32a1",
    "username": "analyst_ops",
    "email": "updated.email@example.com",
    "fullName": "Updated Name",
    "role": "CLIENT",
    "enabled": true,
    "hiddenModules": ["database", "markets"],
    "readOnlyModules": ["forecast"],
    "createdAt": "2026-11-02T16:00:00"
  }
}
```

##### Error Responses:
- `400 Bad Request` — `"You cannot revoke your own active session"`
- `404 Not Found` — `"User not found"`

---

#### 4.1.6 Delete User

Permanently deletes a user from the database, purges their security state cache, and records an audit log entry.

- **Method / Path:** `DELETE /api/users/{id}`
- **Authorization:** `SUPER_ADMIN`
- **Status on Success:** `200 OK`
- **Request Body:** None

##### Success Response (`200 OK`):
```json
{
  "success": true,
  "message": "User deleted successfully"
}
```

##### Error Responses:
- `404 Not Found` — `"User not found"`

---

#### 4.1.7 Get User Audit Logs

Retrieves complete audit logs for administrative actions taken on user accounts, ordered by timestamp descending (`timestamp DESC`).

- **Method / Path:** `GET /api/users/audit-logs`
- **Authorization:** `SUPER_ADMIN`
- **Request Parameters / Body:** None

##### Success Response (`200 OK`):
```json
{
  "success": true,
  "data": [
    {
      "id": "18cf15d3-82ff-4cbb-9d7a-18b7625de021",
      "action": "UPDATE_STATUS",
      "targetUserId": "8b9e59d2-9cf5-4e01-9a74-9b2f63cb32a1",
      "targetUser": "analyst_ops",
      "performedBy": "admin",
      "changes": "{\"enabled\":{\"old\":true,\"new\":false},\"tokenVersion\":1}",
      "timestamp": "2026-11-02T16:15:30"
    },
    {
      "id": "27fa0914-72fe-45ab-811c-23a745124119",
      "action": "REVOKE_SESSIONS",
      "targetUserId": "8b9e59d2-9cf5-4e01-9a74-9b2f63cb32a1",
      "targetUser": "analyst_ops",
      "performedBy": "admin",
      "changes": "{\"revokedSessions\":true,\"tokenVersion\":2}",
      "timestamp": "2026-11-02T16:20:00"
    },
    {
      "id": "31ac4101-52ab-4112-a722-19e481029411",
      "action": "CREATE",
      "targetUserId": "8b9e59d2-9cf5-4e01-9a74-9b2f63cb32a1",
      "targetUser": "analyst_ops",
      "performedBy": "admin",
      "changes": "{\"username\":\"analyst_ops\",\"email\":\"analyst.ops@example.com\",\"fullName\":\"Operations Analyst\",\"role\":\"ADMIN\",\"hiddenModules\":[\"api-logs\"],\"readOnlyModules\":[\"resource-center\"]}",
      "timestamp": "2026-11-02T16:00:00"
    }
  ]
}
```

##### Field Specifications (`UserAuditLog`):
| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | `string` | Unique audit log UUID |
| `action` | `string` | `"CREATE"`, `"UPDATE"`, `"UPDATE_STATUS"`, `"REVOKE_SESSIONS"`, `"DELETE"` |
| `targetUserId` | `string` | Target user's UUID |
| `targetUser` | `string` | Target user's username |
| `performedBy` | `string` | Username of the administrator who performed the action (or `"SYSTEM"`) |
| `changes` | `string` (JSON string) | Serialized JSON containing change diffs or action parameters |
| `timestamp` | `string` | ISO-8601 timestamp of when the action occurred |

---

### 4.2 Authentication & Profile Endpoints (`/api/auth/**`)
*Endpoints used to authenticate users and fetch current session identity.*

---

#### 4.2.1 User Login

Authenticates a user via credentials and returns a signed JWT.

- **Method / Path:** `POST /api/auth/login`
- **Authorization:** Public (Unauthenticated)

##### Request Body (`LoginDTO`):
```json
{
  "username": "johndoe",
  "password": "SecretPassword123!"
}
```
*Note: Users may provide either `"username"` or `"email"` in the payload.*

##### Success Response (`200 OK`):
```json
{
  "success": true,
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "username": "johndoe",
    "email": "johndoe@example.com",
    "fullName": "John Doe",
    "name": "John Doe",
    "role": "ADMIN",
    "enabled": true,
    "hiddenModules": ["api-logs"],
    "readOnlyModules": ["resource-center"]
  }
}
```

##### Error Responses:
- `401 Unauthorized` — `"Invalid username/email or password"`
- `401 Unauthorized` — `"Account is disabled. Please contact administrator."`

---

#### 4.2.2 Send OTP

Sends a one-time login passcode to the user's registered email.

- **Method / Path:** `POST /api/auth/send-otp`
- **Authorization:** Public (Unauthenticated)

##### Request Body (`SendOtpDTO`):
```json
{
  "email": "johndoe@example.com"
}
```

##### Success Response (`200 OK`):
```json
{
  "success": true,
  "message": "OTP sent successfully"
}
```

---

#### 4.2.3 Verify OTP

Verifies an email OTP and returns a signed JWT.

- **Method / Path:** `POST /api/auth/verify-otp`
- **Authorization:** Public (Unauthenticated)

##### Request Body (`VerifyOtpDTO`):
```json
{
  "email": "johndoe@example.com",
  "otp": "482910"
}
```

##### Success Response (`200 OK`):
```json
{
  "success": true,
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "username": "johndoe",
    "email": "johndoe@example.com",
    "fullName": "John Doe",
    "name": "John Doe",
    "role": "ADMIN",
    "enabled": true,
    "hiddenModules": ["api-logs"],
    "readOnlyModules": ["resource-center"]
  }
}
```

##### Error Responses:
- `401 Unauthorized` — `"Invalid or expired OTP"`
- `401 Unauthorized` — `"Account is disabled. Please contact administrator."`

---

#### 4.2.4 Get Current User (`/api/auth/me`)

Retrieves the currently authenticated user's profile and active session parameters.

- **Method / Path:** `GET /api/auth/me`
- **Authorization:** Authenticated (Any valid JWT)

##### Success Response (`200 OK`):
```json
{
  "success": true,
  "user": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "username": "johndoe",
    "fullName": "John Doe",
    "role": "ADMIN",
    "enabled": true,
    "hiddenModules": ["api-logs"],
    "readOnlyModules": ["resource-center"],
    "iat": 1730540000,
    "exp": 1730626400
  }
}
```

---

## 5. Explicit Backend Gaps & Frontend Workarounds

This section clarifies capabilities that **do not currently exist** in the backend API. The frontend implementation must apply the specified workarounds:

| Feature | Backend Reality | Frontend Workaround |
| :--- | :--- | :--- |
| **Fetch User by ID (`GET /api/users/{id}`)** | **Does not exist.** The backend does not expose a single user fetch endpoint. | The frontend should look up user details from the client-side list previously fetched via `GET /api/users`, or use `/api/auth/me` for the current user. |
| **Server-Side Pagination** | **Does not exist.** `GET /api/users` and `GET /api/users/audit-logs` return complete unpaginated lists. | Perform **client-side pagination** (e.g. 10, 25, 50 rows per page) using React state. |
| **Server-Side Filtering & Search** | **Does not exist.** No query parameters (`?role=`, `?status=`, `?search=`) are supported on `GET /api/users`. | Perform **client-side filtering** on `username`, `email`, `fullName`, `role`, and `enabled`. |
| **Server-Side Sorting** | **Does not exist.** Backend always returns users sorted by `createdAt DESC` and audit logs by `timestamp DESC`. | Perform **client-side sorting** (by name, email, role, status, or date) in the UI table component. |
| **Creating Super Admins** | Prohibited via API. `POST /api/users` strictly accepts only `ADMIN` or `CLIENT`. | Disable or omit the `SUPER_ADMIN` option in the "Role" dropdown on the Create User modal. |

---

## 6. Frontend UI Implementation Guide

### 6.1 Users Table View

1. **Columns to Display:**
   - **Username:** Display `username` with immutable badge/style.
   - **Full Name:** Display `fullName` (fallback to `username` if empty).
   - **Email:** Display `email` with mailto link.
   - **Role:** Display badge:
     - `SUPER_ADMIN` (Purple / Crown icon)
     - `ADMIN` (Blue / Shield icon)
     - `CLIENT` (Gray / User icon)
   - **Status:** Display status badge & switch:
     - Enabled: Green badge (`Active`)
     - Disabled: Red badge (`Disabled`)
   - **Created Date:** Format `createdAt` using `date-fns` or native `Intl.DateTimeFormat`.
   - **Actions:** Edit User, Toggle Status, Revoke Sessions, Delete User.

2. **Client-Side Search & Filters:**
   ```typescript
   const filteredUsers = users.filter((u) => {
     const matchesSearch =
       u.username.toLowerCase().includes(search.toLowerCase()) ||
       (u.fullName && u.fullName.toLowerCase().includes(search.toLowerCase())) ||
       u.email.toLowerCase().includes(search.toLowerCase());
     const matchesRole = roleFilter === 'ALL' || u.role === roleFilter;
     const matchesStatus =
       statusFilter === 'ALL' ||
       (statusFilter === 'ENABLED' && u.enabled) ||
       (statusFilter === 'DISABLED' && !u.enabled);
     return matchesSearch && matchesRole && matchesStatus;
   });
   ```

### 6.2 Create User Modal

- **Form Fields:**
  - `username` (Text input, required, label: "Username", note: "Cannot be changed once created").
  - `fullName` (Text input, required, max length: 100).
  - `email` (Email input, required).
  - `role` (Select dropdown: `"CLIENT"` or `"ADMIN"`. Do not include `"SUPER_ADMIN"`).
  - `password` (Password input, optional, note: "Leave blank to auto-generate a secure random password").
  - `hiddenModules` (Checkbox multi-select of standard module IDs).
  - `readOnlyModules` (Checkbox multi-select of standard module IDs).
- **Validation:**
  - Prevent submit if `username`, `email`, or `fullName` is empty.
  - Trim all text inputs before submission.

### 6.3 Edit User Modal

- **Form Fields:**
  - `username` (Disabled/read-only input with tooltip: "Username is permanent and immutable").
  - `fullName` (Editable text input, max length: 100).
  - `email` (Editable email input).
  - `role` (Select dropdown: `"CLIENT"` or `"ADMIN"`).
  - `password` (Password input, optional, note: "Leave blank to keep existing password. Updating password will immediately terminate all user sessions.").
  - `hiddenModules` and `readOnlyModules` (Checkbox multi-selects).

### 6.4 Status Toggle & Confirmation Flow

When an admin toggles the active/disabled status switch:

1. **Prompt Confirmation Dialog:**
   - If disabling: *"Are you sure you want to disable account **{username}**? The user will be immediately logged out and unable to access the system."*
   - If enabling: *"Enable account **{username}**? The user will be able to log in again."*
2. **Execute Call:**
   ```typescript
   await usersApi.updateUserStatus(user.id, !user.enabled);
   ```
3. **Handle Backend Guards Gracefully:**
   - If the backend returns `400 Bad Request` with `"You cannot disable your own account"`, show an error toast: *"Action rejected: You cannot disable your own administrator account."*
   - If the backend returns `400 Bad Request` with `"Cannot disable the last active Super Administrator account"`, show an error toast: *"Action rejected: At least one active Super Administrator account must remain enabled."*

### 6.5 Session Revocation Flow

Provide an explicit **"Revoke All Sessions"** action button in the user row actions menu:

1. **Prompt Confirmation Dialog:**
   - Title: *"Revoke User Sessions"*
   - Description: *"This will instantly invalidate all active JWT tokens and sessions for **{username}**. The user will be required to log in again with their password."*
2. **Execute Call:**
   ```typescript
   await usersApi.revokeUserSessions(user.id);
   ```
3. **Safeguard:**
   - Disable this button for the currently logged-in Super Admin (backend rejects self-revocation).

### 6.6 Delete User Flow

1. **Prompt High-Risk Confirmation Dialog:**
   - Title: *"Delete User Account"*
   - Description: *"Are you sure you want to permanently delete **{username}**? This action cannot be undone."*
2. **Execute Call:**
   ```typescript
   await usersApi.deleteUser(user.id);
   ```
3. **Update UI:** Remove deleted user from client-side state without needing a full page reload.

---

## 7. TypeScript Definitions

Create or update your TypeScript definitions in `src/types/user.ts`:

```typescript
/**
 * User roles in prolt-operations
 */
export type UserRole = 'SUPER_ADMIN' | 'ADMIN' | 'CLIENT';

/**
 * User representation returned by user administration endpoints
 */
export interface UserDTO {
  id: string;
  username: string;
  email: string;
  fullName: string | null;
  role: UserRole;
  enabled: boolean;
  hiddenModules: string[];
  readOnlyModules: string[];
  createdAt: string;
}

/**
 * Payload for creating a new user
 */
export interface CreateUserDTO {
  username: string;
  email: string;
  fullName: string;
  password?: string;
  role: 'ADMIN' | 'CLIENT';
  hiddenModules?: string[];
  readOnlyModules?: string[];
}

/**
 * Payload for updating an existing user
 */
export interface UpdateUserDTO {
  email?: string;
  fullName?: string;
  password?: string;
  role?: 'ADMIN' | 'CLIENT';
  hiddenModules?: string[];
  readOnlyModules?: string[];
}

/**
 * Payload for updating user status
 */
export interface UpdateUserStatusDTO {
  enabled: boolean;
}

/**
 * Audit log entry for administrative user actions
 */
export interface UserAuditLog {
  id: string;
  action: 'CREATE' | 'UPDATE' | 'UPDATE_STATUS' | 'REVOKE_SESSIONS' | 'DELETE';
  targetUserId: string;
  targetUser: string;
  performedBy: string;
  changes: string; // JSON serialized string
  timestamp: string;
}

/**
 * Standard backend successful response wrapper
 */
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

/**
 * Standard backend error response
 */
export interface ApiErrorResponse {
  success: false;
  message: string;
}

/**
 * Current user profile returned by /api/auth/me
 */
export interface AuthUserMe {
  id: string;
  username: string;
  fullName: string | null;
  role: UserRole;
  enabled: boolean;
  hiddenModules: string[];
  readOnlyModules: string[];
  iat: number;
  exp: number;
}
```

---

## 8. Axios API Client Implementation

Replace or update `src/api/users.api.ts` with the fully typed implementation below:

```typescript
import { apiClient } from './client';
import {
  UserDTO,
  CreateUserDTO,
  UpdateUserDTO,
  UserAuditLog,
  ApiResponse,
} from '../types/user';

export const usersApi = {
  /**
   * Retrieve all users (Super Admin only)
   */
  getAllUsers: async (): Promise<UserDTO[]> => {
    const response = await apiClient.get<ApiResponse<UserDTO[]>>('/users');
    return response.data.data;
  },

  /**
   * Create a new user (Super Admin only)
   */
  createUser: async (payload: CreateUserDTO): Promise<UserDTO> => {
    const response = await apiClient.post<ApiResponse<UserDTO>>('/users', payload);
    return response.data.data;
  },

  /**
   * Update an existing user (Super Admin only)
   */
  updateUser: async (id: string, payload: UpdateUserDTO): Promise<UserDTO> => {
    const response = await apiClient.put<ApiResponse<UserDTO>>(`/users/${id}`, payload);
    return response.data.data;
  },

  /**
   * Enable or disable a user account (Super Admin only)
   */
  updateUserStatus: async (id: string, enabled: boolean): Promise<UserDTO> => {
    const response = await apiClient.patch<ApiResponse<UserDTO>>(`/users/${id}/status`, {
      enabled,
    });
    return response.data.data;
  },

  /**
   * Invalidate all active sessions for a user (Super Admin only)
   */
  revokeUserSessions: async (id: string): Promise<UserDTO> => {
    const response = await apiClient.post<ApiResponse<UserDTO>>(
      `/users/${id}/revoke-sessions`
    );
    return response.data.data;
  },

  /**
   * Delete a user permanently (Super Admin only)
   */
  deleteUser: async (id: string): Promise<string> => {
    const response = await apiClient.delete<{ success: boolean; message: string }>(
      `/users/${id}`
    );
    return response.data.message;
  },

  /**
   * Retrieve administrative audit logs (Super Admin only)
   */
  getAuditLogs: async (): Promise<UserAuditLog[]> => {
    const response = await apiClient.get<ApiResponse<UserAuditLog[]>>(
      '/users/audit-logs'
    );
    return response.data.data;
  },
};
```

---

## 9. Error Handling Reference & Status Codes

| Status Code | Reason / Message | Cause | Frontend Action |
| :---: | :--- | :--- | :--- |
| `400` | `"Username or email already exists"` | Duplicate username or email entered | Highlight fields with inline validation error |
| `400` | `"Full name must not exceed 100 characters"` | Full name input > 100 chars | Display form validation error on fullName |
| `400` | `"Valid role is required (ADMIN or CLIENT)"` | Invalid role submitted | Ensure dropdown only provides ADMIN or CLIENT |
| `400` | `"You cannot disable your own account"` | Admin attempted to disable self | Show warning toast; reset toggle switch |
| `400` | `"Cannot disable the last active Super Administrator account"` | Disabling sole active Super Admin | Show warning alert; reset toggle switch |
| `400` | `"You cannot revoke your own active session"` | Admin attempted self-revocation | Show warning toast |
| `401` | `"Account is disabled. Please contact administrator."` | Token or credentials belong to disabled user | Force logout; redirect to login; show alert |
| `401` | `"Unauthorized"` | Token expired or tokenVersion mismatch | Clear stored auth tokens; redirect to `/login` |
| `403` | `"Forbidden: Super Admin access required"` | Non-Super Admin accessed `/api/users/**` | Redirect user away from User Management page |
| `404` | `"User not found"` | Target user ID does not exist in DB | Refresh user list table; notify user |
| `500` | `"Something went wrong. Please try again later."` | Uncaught backend exception | Show generic error toast |

---

## 10. API Coverage & Verification Matrix

| Endpoint | Method | Role Required | Backend Implemented | Test Coverage |
| :--- | :---: | :---: | :---: | :---: |
| `/api/users` | `GET` | `SUPER_ADMIN` | ✅ Yes | Fully Tested |
| `/api/users/audit-logs` | `GET` | `SUPER_ADMIN` | ✅ Yes | Fully Tested |
| `/api/users` | `POST` | `SUPER_ADMIN` | ✅ Yes | Fully Tested |
| `/api/users/{id}` | `PUT` | `SUPER_ADMIN` | ✅ Yes | Fully Tested |
| `/api/users/{id}/status` | `PATCH` | `SUPER_ADMIN` | ✅ Yes | Fully Tested |
| `/api/users/{id}/revoke-sessions` | `POST` | `SUPER_ADMIN` | ✅ Yes | Fully Tested |
| `/api/users/{id}` | `DELETE` | `SUPER_ADMIN` | ✅ Yes | Fully Tested |
| `/api/users/{id}` | `GET` | N/A | ❌ **No (Backend Gap)** | Documented Workaround |
| `/api/auth/login` | `POST` | Public | ✅ Yes | Fully Tested |
| `/api/auth/send-otp` | `POST` | Public | ✅ Yes | Fully Tested |
| `/api/auth/verify-otp` | `POST` | Public | ✅ Yes | Fully Tested |
| `/api/auth/me` | `GET` | Authenticated | ✅ Yes | Fully Tested |

---

*This guide was generated directly from the inspected Spring Boot backend implementation in `prolt-operations`.*

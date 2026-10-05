# API Endpoints

## Authentication (`/api/v1/auth`)

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|----------------|
| POST | `/api/v1/auth/login` | Authenticates a user and issues a short-lived access token plus a long-lived refresh token. Creates a new session record tagged with device IP and User-Agent. | No |
| POST | `/api/v1/auth/refresh-token` | Exchanges a valid refresh token for a new access token. The refresh token is rotated (old one invalidated). | No |
| POST | `/api/v1/auth/logout` | Revokes all active sessions for the current authenticated user. | Yes |
| POST | `/api/v1/auth/logout-all` | Revokes every active refresh token belonging to the authenticated user, ending all sessions across every device. | Yes |
| GET | `/api/v1/auth/sessions` | Returns all currently active (non-expired, non-revoked) login sessions for the authenticated user, including device name, type, IP address, and creation time. | Yes |
| DELETE | `/api/v1/auth/sessions/{sessionId}` | Ends one specific device's session by its session ID, without affecting other active sessions. A user may only revoke their own sessions. | Yes |

## Current user (`/api/v1/users/me`)

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|----------------|
| GET | `/api/v1/users/me` | Returns the authenticated caller's own profile (`UserResponse`), resolved from the access token. The login response only contains tokens, so clients call this right after login to populate the session with id/name/role/etc. | Yes |

## Tasks (`/api/v1/tasks`)

All endpoints require a bearer token (`Authorization: Bearer <accessToken>`). Every response is wrapped in the standard envelope:

```ts
type ApiResponse<T> = {
  status?: string
  message: string
  success: boolean
  data: T
  metadata?: unknown
  timestamp: string // ISO instant
}
```

Shared enums:

```ts
type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED' | 'DONE' | 'BLOCKED' | 'CANCELLED'
type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
```

`TaskResponse` (returned by every endpoint below, either alone or inside a page):

```ts
type TaskResponse = {
  id: string          // UUID
  taskCode: string
  title: string
  description: string | null
  status: TaskStatus
  priority: Priority
  creator: UserRef | null
  assignedBy: UserRef | null
  assignee: UserRef | null
  department: DepartmentRef
  parentTaskId: string | null   // UUID
  startDate: string | null      // LocalDateTime, e.g. "2026-08-15T09:00:00"
  dueDate: string | null        // LocalDate, e.g. "2026-08-20"
  completedAt: string | null    // LocalDate
  createdAt: string             // Instant
  updatedAt: string             // Instant
  progress: number | null
}

type UserRef = { id: string; name: string; email: string; avatarUrl: string | null }
type DepartmentRef = { id: string; name: string }
```

---

### 1. Create task

`POST /api/v1/tasks` — requires authority `tasks:create`

Request body (`CreateTaskRequest`):

```ts
type CreateTaskRequest = {
  title: string            // required, max 500 chars
  description?: string
  priority: Priority       // required
  status?: TaskStatus      // accepted but ignored — server always creates as TODO
  departmentId: string     // required, UUID
  assigneeId?: string      // UUID — if set, task is assigned immediately and an assignment notification fires
  parentTaskId?: string    // UUID
  startDate?: string       // LocalDate, must be today or future
  dueDate?: string         // LocalDate, must be today or future
  estimatedHours?: number
  tags?: string[]
}
```

Response: `201 Created`, `ApiResponse<TaskResponse>`.
Errors: `400` validation failure, `403` missing `tasks:create` (or missing assign permission when `assigneeId` is set), `404` department/assignee not found.

### 2. Search / list tasks

`GET /api/v1/tasks` — no explicit authority required; results are scoped server-side to what the caller is permitted to see (own tasks / team / department / all, based on their permissions).

Query params — all optional, combine with AND (`TaskFilterRequest`), plus standard Spring pagination/sort params:

```ts
type TaskSearchParams = {
  taskStatus?: TaskStatus
  priority?: Priority
  departmentId?: string   // UUID
  assigneeId?: string     // UUID
  createdById?: string    // UUID
  titleContains?: string  // case-insensitive substring match on title
  page?: number            // default 0
  size?: number             // default 20
  sort?: string             // e.g. "createdAt,desc"
}
```

Response: `200`, `ApiResponse<PageResponse<TaskResponse>>` where:

```ts
type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  empty: boolean
}
```

### 3. Get a task

`GET /api/v1/tasks/{id}` — no explicit authority required; service rejects with `403` if the caller can't view this specific task (not creator/assignee/department/no view-all permission).

Response: `200`, `ApiResponse<TaskResponse>`.
Errors: `403`, `404` task not found.

### 4. Update a task

`PUT /api/v1/tasks/{id}` — does **not** change status or assignee; only editable fields below.

Request body (`UpdateTaskRequest`):

```ts
type UpdateTaskRequest = {
  title: string          // required, max 500 chars
  description?: string
  priority: Priority     // required
  dueDate?: string       // LocalDate — no future/present restriction, editing an overdue task is allowed
}
```

Response: `200`, `ApiResponse<TaskResponse>`.
Errors: `400` validation, `403` not permitted, `404` task not found.

### 5. Assign a task

`PATCH /api/v1/tasks/{id}/assignee` — requires authority `tasks:assign:all` or `tasks:assign:department`

Request body (`AssignTaskRequest`):

```ts
type AssignTaskRequest = {
  assigneeId: string   // required, UUID
}
```

Response: `200`, `ApiResponse<TaskResponse>`. Fires a "task assigned" notification to the new assignee.
Errors: `400` missing assigneeId, `403` not permitted (e.g. department-scoped assigner and assignee outside their department), `404` task/assignee not found.

### 6. Change task status

`PATCH /api/v1/tasks/{id}/status` — no explicit controller-level authority; the workflow engine enforces which transitions the caller may perform (e.g. only the assignee can move `TODO → IN_PROGRESS`, only a reviewer can move `IN_REVIEW → APPROVED/REJECTED`).

Request body — plain object, not a typed DTO:

```ts
type ChangeStatusRequest = {
  status: TaskStatus   // target status, e.g. "IN_PROGRESS"
}
```

Response: `200`, `ApiResponse<TaskResponse>`. Fires a "status changed" notification to the assignee if the actor isn't the assignee themselves.
Errors: `400` invalid/missing status or illegal transition, `403` not permitted for this transition, `404` task not found, `409` if the task was modified concurrently (optimistic-lock conflict — reload and retry).

Valid transitions:

```
TODO        → IN_PROGRESS, CANCELLED
IN_PROGRESS → IN_REVIEW, CANCELLED
IN_REVIEW   → APPROVED, REJECTED
REJECTED    → IN_PROGRESS, CANCELLED
APPROVED    → DONE
DONE        → (terminal)
CANCELLED   → (terminal)
```

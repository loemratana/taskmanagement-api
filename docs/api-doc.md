# Task API

Base path: `/api/v1/tasks`
Auth: Bearer JWT on all endpoints (`Authorization: Bearer <token>`)

All responses are wrapped:

```json
{
  "status": "string | null",
  "message": "string",
  "success": true,
  "data": { /* endpoint-specific payload, omitted if null */ },
  "metadata": null,
  "timestamp": "2026-08-19T10:00:00Z"
}
```

Error responses:

```json
{
  "success": false,
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/tasks",
  "timestamp": "2026-08-19T10:00:00Z",
  "fieldErrors": [
    { "field": "title", "message": "must not be blank" }
  ]
}
```

## Enums

**Priority**: `LOW` | `MEDIUM` | `HIGH` | `URGENT`

**TaskStatus**: `TODO` | `IN_PROGRESS` | `IN_REVIEW` | `APPROVED` | `REJECTED` | `DONE` | `BLOCKED` | `CANCELLED`

Allowed status transitions (enforced server-side on `PATCH /{id}/status`):

| From | Allowed to |
|---|---|
| TODO | IN_PROGRESS, CANCELLED |
| IN_PROGRESS | IN_REVIEW, CANCELLED |
| IN_REVIEW | APPROVED, REJECTED |
| REJECTED | IN_PROGRESS, CANCELLED |
| APPROVED | DONE |
| DONE | *(terminal)* |
| CANCELLED | *(terminal)* |
| BLOCKED | *(not currently reachable via transition map — check with backend before relying on it)* |

---

## 1. Create task

`POST /api/v1/tasks`

Required authority: `tasks:create`

**Request body** (`CreateTaskRequest`):

```json
{
  "title": "string, required, max 500 chars",
  "description": "string, optional",
  "priority": "LOW | MEDIUM | HIGH | URGENT, required",
  "departmentId": "uuid, required",
  "assigneeId": "uuid, optional",
  "parentTaskId": "uuid, optional",
  "startDate": "YYYY-MM-DD, optional, must be today or future",
  "dueDate": "YYYY-MM-DD, optional, must be today or future",
  "estimatedHours": "number, optional",
  "tags": ["string", "..."]
}
```

Note: `status` is not accepted — new tasks always start as `TODO`.

**Response**: `201 Created`, `data` = `TaskResponse` (see [Task object](#task-object)).

---

## 2. Search tasks

`GET /api/v1/tasks`

No explicit authority annotation — results are scoped server-side to what the caller can see (own / team / department / all, depending on their permissions).

**Query params** — all optional, combine with AND, plus standard Spring paging/sort:

| Param | Type | Notes |
|---|---|---|
| `taskStatus` | TaskStatus | |
| `priority` | Priority | |
| `departmentId` | uuid | |
| `assigneeId` | uuid | |
| `createdById` | uuid | |
| `titleContains` | string | substring match |
| `page` | int | default 0 |
| `size` | int | default 20 |
| `sort` | string | e.g. `createdAt,desc` |

Example: `GET /api/v1/tasks?taskStatus=IN_PROGRESS&priority=HIGH&page=0&size=20&sort=createdAt,desc`

**Response**: `200 OK`, `data` = `PageResponse<TaskResponse>`:

```json
{
  "content": [ /* TaskResponse[] */ ],
  "page": 0,
  "size": 20,
  "totalElements": 147,
  "totalPages": 8,
  "first": true,
  "last": false,
  "empty": false
}
```

---

## 3. Get task by id

`GET /api/v1/tasks/{id}`

**Response**: `200 OK`, `data` = `TaskResponse`.

---

## 4. Update task

`PUT /api/v1/tasks/{id}`

Updates title/description/priority/dueDate only — **does not** change status or assignee (use the dedicated endpoints below for those).

**Request body** (`UpdateTaskRequest`):

```json
{
  "title": "string, required, max 500 chars",
  "description": "string, optional",
  "priority": "LOW | MEDIUM | HIGH | URGENT, required",
  "dueDate": "YYYY-MM-DD, optional"
}
```

**Response**: `200 OK`, `data` = `TaskResponse`.
`403` if not permitted, `404` if task not found (see `ErrorResponse` shape above).

---

## 5. Assign task

`PATCH /api/v1/tasks/{id}/assignee`

Required authority: `tasks:assign:all` OR `tasks:assign:department`

**Request body** (`AssignTaskRequest`):

```json
{
  "assigneeId": "uuid, required"
}
```

**Response**: `200 OK`, `data` = `TaskResponse`.

---

## 6. Change task status

`PATCH /api/v1/tasks/{id}/status`

**Request body** (raw map, not a validated DTO):

```json
{
  "status": "IN_PROGRESS"
}
```

Must be a valid transition from the task's current status (see transition table above); invalid transitions are rejected server-side.

**Response**: `200 OK`, `data` = `TaskResponse`.

---

## Task object

`TaskResponse`, returned by all endpoints above as `data` (or inside `content[]` for search):

```json
{
  "id": "uuid",
  "taskCode": "string",
  "title": "string",
  "description": "string",
  "status": "TaskStatus",
  "priority": "Priority",
  "creator": { "id": "uuid", "name": "string", "email": "string", "avatarUrl": "string" },
  "assignedBy": { "id": "uuid", "name": "string", "email": "string", "avatarUrl": "string" },
  "assignee": { "id": "uuid", "name": "string", "email": "string", "avatarUrl": "string" },
  "department": { "id": "uuid", "name": "string" },
  "parentTaskId": "uuid",
  "startDate": "2026-08-19T10:00:00",
  "dueDate": "2026-08-25",
  "completedAt": "2026-08-25",
  "createdAt": "2026-08-19T10:00:00Z",
  "updatedAt": "2026-08-19T10:00:00Z",
  "progress": 0
}
```

`creator`, `assignedBy`, `assignee`, `department` may be `null` if not set.

---

# Department API

Base path: `/api/v1/departments`
Auth: Bearer JWT on all endpoints. Response envelope and error shape are the same as [above](#task-api).

## 1. List departments (flat)

`GET /api/v1/departments`

Returns all active departments as a flat list — no hierarchy nesting (tree endpoint is currently commented out server-side, not available).

**Response**: `200 OK`, `data` = `DepartmentResponse[]`.

---

## 2. Get department by id

`GET /api/v1/departments/{id}`

**Response**: `200 OK`, `data` = `DepartmentResponse`.
`404` if not found.

---

## 3. Create department

`POST /api/v1/departments`

Required role: `ADMIN`

**Request body** (`DepartmentRequest`):

```json
{
  "name": "string, required, max 150 chars",
  "description": "string, optional",
  "managerId": "uuid, optional"
}
```

**Response**: `201 Created`, `data` = `DepartmentResponse`.
`403` insufficient permissions, `409` department code/name already exists.

---

## 4. Update department

`PUT /api/v1/departments/{id}`

Required role: `ADMIN`

**Request body**: same as create (`DepartmentRequest`).

**Response**: `200 OK`, `data` = `DepartmentResponse`.
`404` not found, `409` code already exists.

---

## 5. Delete department

`DELETE /api/v1/departments/{id}`

Required role: `ADMIN`

Soft-delete (deactivates). Fails if the department has active child departments.

**Response**: `200 OK`, `data` = none (message only).
`409` has active child departments, `404` not found.

---

## 6. List department members

`GET /api/v1/departments/{id}/members`

Returns active users belonging to this department, as a minimal picker-friendly DTO. Purpose-built for populating assignee dropdowns (e.g. the Create Task form) — unlike `GET /api/v1/users?departmentId=...`, this endpoint carries no `users:view` authority requirement, so any authenticated user (e.g. someone who only has `tasks:create`) can call it.

**Response**: `200 OK`, `data` = `DepartmentMemberResponse[]`, sorted by `fullName`.
`404` if department not found.

```json
[
  {
    "id": "uuid",
    "fullName": "string",
    "email": "string",
    "avatarUrl": "string",
    "position": { "id": "uuid", "name": "string" }
  }
]
```

`position` is `null` if not set.

---

## 7. Assign department manager

`PUT /api/v1/departments/{departmentId}/manager`

**Request body** (`AssignManagerRequest`):

```json
{
  "managerId": "uuid"
}
```

**Response**: `200 OK`, `data` = `DepartmentResponse`.

---

## Department object

`DepartmentResponse`:

```json
{
  "id": "uuid",
  "name": "string",
  "description": "string",
  "manager": { "id": "uuid", "name": "string", "email": "string", "profile": "string" },
  "avatar": "string",
  "active": true,
  "createdAt": "2026-08-19T10:00:00Z",
  "updatedAt": "2026-08-19T10:00:00Z"
}
```

`manager` is `null` if no manager is assigned.

---

# User API

Base path: `/api/v1/users`
Auth: Bearer JWT on all endpoints. Response envelope and error shape are the same as [above](#task-api).

## 1. Create user

`POST /api/v1/users`

Required authority: `users:create`

**Request body** (`UserCreateRequest`):

```json
{
  "email": "string, required, valid email",
  "password": "string, required, 8-100 chars",
  "fullName": "string, required, max 200 chars",
  "positionId": "uuid, optional",
  "managerId": "uuid, optional",
  "role": ["uuid", "..."],
  "avatarUrl": "string, optional"
}
```

**Response**: `201 Created`, `data` = `UserResponse`.
`400` validation failed, `409` email already in use.

---

## 2. Search users

`GET /api/v1/users`

Required authority: `users:view`

**Query params** — all optional, combine with AND, plus standard Spring paging/sort:

| Param | Type | Notes |
|---|---|---|
| `Keyword` | string | note the capital K — matches the DTO field name as-is |
| `departmentId` | uuid | |
| `positionId` | uuid | |
| `managerId` | uuid | |
| `roleId` | uuid | |
| `userStatus` | UserStatus | `ACTIVE` \| `INACTIVE` \| `SUSPENDED` |
| `active` | boolean | |
| `page` | int | default 0 |
| `size` | int | default 20 |
| `sort` | string | e.g. `fullName,asc` |

**Response**: `200 OK`, `data` = `PageResponse<UserResponse>` (same page wrapper shape as [Search tasks](#2-search-tasks)).

---

## 3. Get current user (me)

`GET /api/v1/users/me`

Resolves the caller from the access token — no id needed. Intended to populate the client session right after login, since the login response only contains tokens.

**Response**: `200 OK`, `data` = `UserResponse`.

---

## 4. Get user by id

`GET /api/v1/users/{id}`

**Response**: `200 OK`, `data` = `UserResponse`.

---

## 5. Update user

`PUT /api/v1/users/{id}`

Only non-null fields in the request body are applied (partial update semantics despite being a PUT).

**Request body** (`UserUpdateRequest`):

```json
{
  "email": "string, optional, valid email, max 255 chars",
  "fullName": "string, optional, max 200 chars",
  "avartarUrl": "string, optional",
  "positionId": "uuid, optional",
  "managerId": "uuid, optional"
}
```

Note: the avatar field name is `avartarUrl` (typo in the API as implemented) — use exactly that key.

**Response**: `200 OK`, `data` = `UserResponse`.
`404` not found, `409` email already in use.

---

## 6. Deactivate user (soft delete)

`DELETE /api/v1/users/{id}`

Sets `is_active = false` and `status = INACTIVE`. No rows are ever hard-deleted (users are referenced by tasks/approvals/logs).

**Response**: `204 No Content`, no body.
`404` not found.

---

## 7. Reactivate user

`PATCH /api/v1/users/{id}/activate`

**Response**: `204 No Content`, no body.
`404` not found.

---

## 8. Replace user's roles

`PUT /api/v1/users/{id}/roles`

Replaces the entire role set — **not additive**. Send the full desired set of role ids each time.

**Request body** (`AssignRolesRequest`):

```json
{
  "roleIds": ["uuid", "..."]
}
```

`roleIds` must be non-empty.

**Response**: `200 OK`, `data` = `UserResponse`.
`400` one or more role ids do not exist, `404` user not found.

---

## 9. Upload avatar

`POST /api/v1/users/{id}/avatar`

Content type: `multipart/form-data`, field name `file`.

**Response**: `200 OK`, `data` = `UserResponse`.

---

## User object

`UserResponse`:

```json
{
  "id": "uuid",
  "email": "string",
  "fullName": "string",
  "avatarUrl": "string",
  "status": "ACTIVE | INACTIVE | SUSPENDED",
  "active": true,
  "lastLoginAt": "2026-08-19T10:00:00Z",
  "createdAt": "2026-08-19T10:00:00Z",
  "position": { "id": "uuid", "name": "string" },
  "department": { "id": "uuid", "name": "string" },
  "manager": { "id": "uuid", "name": "string" },
  "roles": [ { "id": "uuid", "name": "string" } ]
}
```

`position`, `department`, `manager` are `null` if not set; `roles` may be an empty array.

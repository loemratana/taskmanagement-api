# Task Visibility by Role — Analysis

## Goal

| Actor | What they should see |
|-------|----------------------|
| **Admin** | All tasks in the system |
| **Manager** | Their own tasks + tasks assigned to their team members |
| **Employee** | Only their own tasks (assignee and/or creator) |

---

## Recommended design (production)

Do **not** branch on role names in services (`if (role.equals("ADMIN"))`).

Use this stack instead:

```
Role (ADMIN / MANAGER / EMPLOYEE)
  → permissions on the role (DB)
    → JWT authorities on UserPrincipal
      → TaskAuthorizationService.resolveViewScope(actor)
        → ViewScope (ALL | TEAM | DEPARTMENT | OWN)
          → TaskSpecifications.visibleTo(...) on list queries
          → canViewTask / assertCanView on get-by-id
```

Permissions decide **capability**.  
ViewScope decides **how wide the query is**.  
Data (`department_id`, `manager_id`) decides **which rows match**.

---

## View scopes

| Scope | Meaning | Typical role |
|-------|---------|--------------|
| `ALL` | No row filter | Admin |
| `TEAM` | Own + tasks whose assignee reports to me (`user.manager_id = me`) | Team manager |
| `DEPARTMENT` | Tasks in my department | Department manager (optional) |
| `OWN` | Assignee or creator is me | Employee |

Defined in `ViewScope`.

---

## Permission → scope mapping

| Permission | Resolves to |
|------------|-------------|
| `tasks:view:all` (or `tasks:assign:all`) | `ALL` |
| `tasks:view:department` (or `tasks:assign:department`) | `DEPARTMENT` |
| `tasks:view:team` (or `tasks:assign:team`) | `TEAM` |
| `tasks:view:assigned` / none of the above | `OWN` |

**Resolve order (highest wins):**

1. `ALL`
2. `DEPARTMENT`
3. `TEAM`
4. `OWN`

Constants live in `TaskPermissions`.

---

## Suggested role seed

| Role | View permissions (minimum) |
|------|----------------------------|
| `ADMIN` | `tasks:view:all` |
| `MANAGER` | `tasks:view:team` |
| `EMPLOYEE` | `tasks:view:assigned` |

Optional for a department head:

| Role | View permissions |
|------|------------------|
| `DEPARTMENT_MANAGER` | `tasks:view:department` |

Also give employees write/workflow permissions they need, e.g.:

- `tasks:update:own`
- `tasks:transition:advance:own`

---

## Team vs department (important)

These are **different products**:

| Model | Filter | Data required |
|-------|--------|----------------|
| **Team** (“see my members”) | Assignee is a direct report | `users.manager_id` set correctly |
| **Department** (“see my dept”) | `task.department_id = actor.department_id` | Users and tasks have department |

For “manager sees member tasks in team”, use **`TEAM`**, not department.

Helper already exists:

```text
UserRepository.findDirectReportIds(managerId)
```

---

## Runtime flow

### List tasks (`GET /api/v1/tasks`)

```text
1. Load UserPrincipal from JWT
2. scope = authorizationService.resolveViewScope(actor)
3. if scope == TEAM:
     memberIds = userRepository.findDirectReportIds(actor.id)
4. page = taskRepository.findAll(
       TaskSpecifications.visibleTo(actor, scope, memberIds)
         .and(optional filters),
       pageable)
5. map → TaskResponse page
```

Filtering must happen **in SQL** (Specification), not by loading all tasks into memory.

### Get one task (`GET /api/v1/tasks/{id}`)

```text
1. Load task
2. authorizationService.assertCanView(actor, task)
3. return TaskResponse
```

`canViewTask` must honor the same rules as list (`ALL` / `DEPARTMENT` / `TEAM` / `OWN`).  
Otherwise a manager can see a member task in the list but get **403** on open.

---

## What already exists in this codebase

| Piece | Status |
|-------|--------|
| `ViewScope` (`OWN`, `TEAM`, `DEPARTMENT`, `ALL`) | Present |
| `tasks:view:*` permission constants | Present (incl. `tasks:view:team`) |
| `TaskAuthorizationService.resolveViewScope` | Present but **does not return `TEAM` yet** |
| `TaskAuthorizationService.canViewTask` | Present but **no team check yet** |
| `TaskSpecifications.visibleTo(...)` | Present |
| `User.manager` + `findDirectReportIds` | Present |
| `GET /api/v1/tasks` list endpoint | **Missing** |
| Role → permission DB seed for view scopes | **Missing / verify in your data** |

There is also an older `specification/TaskSpecification.java` (filter helpers). Prefer one path: use `TaskSpecifications.visibleTo` for authz scope, and layer UI filters on top.

---

## Gaps to close (when implementing)

1. **`resolveViewScope`** — insert `TEAM` between department and own.
2. **`canViewTask`** — allow manager if assignee’s `manager_id == actor.id` (or assignee is in direct-report IDs).
3. **`listTasks`** on `TaskService` + `TaskController` — apply `visibleTo` + pagination.
4. **Seed roles/permissions** — Admin / Manager / Employee as above.
5. **Data quality** — every employee must have `manager_id` pointing at their manager, or `TEAM` scope is empty.

---

## Anti-patterns to avoid

- Hardcoding `ROLE_ADMIN` / `ROLE_MANAGER` inside `TaskServiceImpl`
- Returning all tasks then filtering in Java for large datasets
- Different rules for list vs get-by-id
- Using department scope when the product requirement is “my team members”

---

## Summary

The intended production pattern is already sketched in this project: **permissions → ViewScope → Specification**.

To match “admin sees all, manager sees team, employee sees own”:

1. Put the right `tasks:view:*` permissions on each role.
2. Keep `manager_id` accurate for the team hierarchy.
3. Finish scope resolution + list endpoint so queries enforce that scope in the database.

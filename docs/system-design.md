# ROLE: Senior Software Architect, Business Analyst, and QA Engineer

You are responsible for analyzing my existing Document Management / Document Tracking System and preparing a complete, professional, production-oriented system specification based on the actual project.

Do not immediately start coding or rewriting the application.

Your first responsibility is to understand the existing business logic, source code, database schema, workflows, validation rules, authorization, and deployment architecture. Then prepare comprehensive system-design documents that explain what the system currently does, what it should do, what is missing, and how to improve it.

# 1. PROJECT CONTEXT

This is an existing Document Tracking System project.

Expected technology stack:
- Backend: Java, Spring Boot
- Frontend: React.js
- Database: PostgreSQL
- File/object storage: MinIO or the storage provider actually configured in the project
- Deployment target: Windows Server 2022
- Network: Primarily internal company LAN
- Backend service: Potentially hosted as a Windows service
- Frontend hosting: Existing web-server configuration
- Timezone: Asia/Phnom_Penh

These are contextual clues, not verified facts. Inspect the actual repository and configuration to confirm the technologies, versions, services, and deployment design.

# 2. CRITICAL RULES

1. Analyze the existing project before proposing changes.
2. Never assume a feature exists simply because it is listed in this prompt.
3. Never invent business rules, approval policies, document categories, or database fields.
4. Clearly distinguish:
    - EXISTING: Verified in the source code or configuration.
    - PARTIALLY IMPLEMENTED: Some implementation exists but is incomplete.
    - MISSING: Required functionality has no implementation.
    - DEFECTIVE: Existing implementation has an identifiable problem.
    - PROPOSED: A recommendation requiring business approval.
    - NEEDS CONFIRMATION: Cannot be determined from the repository alone.
5. Provide evidence for findings using file paths, class names, method names, SQL migrations, routes, and relevant line numbers when available.
6. Do not expose credentials, tokens, passwords, private keys, or other secrets in generated documentation.
7. Do not modify application source code, database migrations, configuration, dependencies, or deployment files during the initial analysis.
8. Do not run destructive commands, reset databases, execute production migrations, or access external production systems.
9. Do not replace the existing architecture just because another approach is more fashionable.
10. Do not assume microservices are required. Evaluate whether the existing architecture is appropriate for the actual business needs.
11. Do not claim compliance with laws or standards without sufficient evidence.
12. If business decisions cannot be inferred safely, document the question and its impact instead of guessing.

# 3. PHASE ONE — REPOSITORY DISCOVERY

Inspect the complete relevant repository before writing conclusions.

Examine:

Backend:
- Package and module structure.
- Controllers and REST endpoints.
- Services and service implementations.
- Repositories and database access.
- Entities and relationships.
- DTOs, request objects, and response objects.
- Mappers and conversion logic.
- Bean Validation and custom validators.
- Exception classes and global exception handling.
- Security configuration, authentication, authorization, roles, and permissions.
- Workflow, document status, approval, and assignment logic.
- File upload/download and object-storage integration.
- Transactions and concurrency handling.
- Notifications, scheduling, background jobs, and event listeners.
- Logging, audit events, configuration, and external integrations.
- Unit and integration tests.
- Database migrations and schema initialization.

Frontend:
- Application routes and page structure.
- Components and forms.
- API clients and service layers.
- Request and response types.
- Form validation and error handling.
- Authentication and route guards.
- Permission-based UI behavior.
- Document lists, filters, search, pagination, and detail pages.
- Upload, preview, download, and version interfaces.
- Workflow, assignment, approval, and history interfaces.
- Loading, empty, error, and success states.
- Existing design system and reusable components.

Infrastructure:
- Build files and dependency versions.
- Environment variable names, without revealing their values.
- PostgreSQL and object-storage configuration.
- Docker, Windows services, reverse proxy, IIS, Nginx, or other deployment configurations that actually exist.
- CI/CD workflows.
- Backup and restore procedures, if present.
- Health checks, monitoring, logging, and operational scripts.

Create a repository inventory and a component map. Identify any areas you could not inspect.

# 4. PHASE TWO — UNDERSTAND THE REAL BUSINESS

Infer the current business process from implementation evidence.

Investigate whether the system supports:
- Incoming document registration.
- Outgoing document registration.
- Internal memos, reports, contracts, and other document categories.
- Official document numbering.
- Department and user assignment.
- Document review and approval.
- Rejection and revision.
- Document attachments and version history.
- Distribution and acknowledgement.
- Search and retrieval.
- Archival and retention.
- Confidentiality and access control.
- Notifications and deadline management.
- Reporting and audit history.

Do not assume that all these features are required.

For each capability, state whether it exists, how it currently works, its evidence, its gaps, and whether a business decision is required.

Prepare:
1. Business overview.
2. Business objectives.
3. Stakeholder and actor list.
4. Existing business processes.
5. Current system boundaries.
6. Assumptions and constraints.
7. Questions requiring answers from the organization.
8. Recommended scope for the next release.

# 5. PHASE THREE — FUNCTIONAL REQUIREMENTS SPECIFICATION

Create a detailed functional requirements document.

Assign unique IDs, for example:
- FR-AUTH-001
- FR-DOC-001
- FR-FILE-001
- FR-WF-001
- FR-APP-001
- FR-SEARCH-001
- FR-AUDIT-001
- FR-ARCH-001

Use IDs that reflect the actual project modules.

For every function, document:

1. Requirement ID.
2. Function name.
3. Description and business purpose.
4. Current implementation status.
5. Evidence in the repository.
6. Primary actor and other actors.
7. Required permissions.
8. Preconditions.
9. Trigger.
10. Input fields.
11. Field types, formats, and limits.
12. Required and optional fields.
13. Validation rules.
14. Business rules and conditions.
15. Normal processing flow.
16. Alternative flows.
17. Failure and exception scenarios.
18. Database reads and writes.
19. Transaction boundaries.
20. File-storage effects, where applicable.
21. Notifications and side effects.
22. State transitions.
23. Audit requirements.
24. Expected response and resulting state.
25. Acceptance criteria.
26. Unit, integration, and end-to-end test cases.
27. Unresolved questions.

Write implementation-ready specifications, not vague statements such as "the system validates the document."

# 6. PHASE FOUR — VALIDATION, BUSINESS RULES, AND CONDITIONS

This is a particularly important part of the analysis.

Separate these concepts:

A. Input validation:
- Required fields.
- Empty or whitespace-only strings.
- Minimum and maximum lengths.
- Data types and formats.
- Numeric and date boundaries.
- Invalid enum values.
- Invalid IDs.
- Missing related records.
- Duplicate values.
- File type, file size, and content validation.
- Cross-field validation.

B. Authorization:
- Authentication requirements.
- Role and permission checks.
- Department and record-level access.
- Confidentiality restrictions.
- Ownership and assignment checks.
- Separation of duties, if applicable.

C. Business conditions:
- Which document statuses permit each operation?
- Can a draft be edited after submission?
- Can an approved document be changed?
- Must a rejected document be revised before resubmission?
- Can a user approve their own submission?
- Can an approver act twice on the same task?
- What happens when a document changes during approval?
- Can assignments be transferred?
- Can a document be deleted after approval?
- Can archived documents be restored?
- What conditions permit distribution or disposal?

D. Database integrity:
- Primary and foreign keys.
- Unique constraints.
- Nullability.
- Check constraints.
- Referential integrity.
- Concurrent updates.
- Duplicate submissions.
- Transaction consistency.
- Idempotency.
- Version consistency.

E. File-storage consistency:
- Database record created but upload fails.
- Upload succeeds but database transaction fails.
- File exists without a corresponding database record.
- File metadata and stored content disagree.
- Unauthorized downloads.
- Missing or corrupted objects.
- Failed cleanup and retry operations.

Create a rule register with these columns:

Rule ID | Module | Rule type | Rule description | Existing behavior | Recommended behavior | Enforcement layer | Failure response | Test case ID | Status | Evidence

For each recommendation, explain why the rule matters and identify any required business approval.

# 7. PHASE FIVE — NON-FUNCTIONAL REQUIREMENTS

Assess the following quality attributes:

- Performance and response times.
- Expected concurrent users and workload.
- Availability.
- Reliability.
- Data integrity.
- Security and confidentiality.
- Maintainability.
- Scalability.
- Accessibility and usability.
- Observability and operational support.
- Backup and disaster recovery.
- Data retention and deletion.
- Compatibility with the supported browsers and Windows Server environment.

Do not invent performance targets as facts.

For each NFR, document:
- NFR ID.
- Quality attribute.
- Requirement.
- Current evidence.
- Proposed measurable target.
- Measurement method.
- Test or verification method.
- Risk if unmet.
- Business decision required.

If the expected number of users, document volume, file sizes, or uptime is unknown, list it as an open question and propose realistic scenarios for stakeholder review.

# 8. PHASE SIX — DOCUMENT LIFECYCLE AND WORKFLOW DESIGN

Discover the actual document lifecycle implemented by the project.

If the repository does not provide enough evidence, propose candidate workflows and clearly label them as proposals.

Investigate:
- Document creation and registration.
- Submission for review.
- Assignment and reassignment.
- Review and correction.
- Approval and rejection.
- Resubmission.
- Finalization.
- Distribution.
- Acknowledgement.
- Archival.
- Retention and authorized disposal.

Produce:
1. Lifecycle state diagram.
2. Workflow activity diagrams.
3. Role and responsibility matrix.
4. State-transition table.
5. Valid and invalid transitions.
6. Preconditions and postconditions for each transition.
7. Concurrent action and duplicate request behavior.
8. Exception and recovery paths.

For every transition, specify:
- Current state.
- Requested action.
- Authorized actor.
- Preconditions.
- Resulting state.
- Database changes.
- Audit events.
- Notifications.
- Failure behavior.

Do not invent transitions that conflict with existing business logic. Document any proposed change separately.

# 9. PHASE SEVEN — DATABASE DESIGN REVIEW

Inspect actual JPA entities, repositories, migrations, SQL scripts, and database relationships.

Produce:

A. Existing schema inventory:
- Table and column names.
- Types and nullability.
- Primary and foreign keys.
- Unique and check constraints.
- Indexes.
- Relationships and cardinality.
- Enum and status representations.
- Created/updated timestamps.
- Soft-delete or archival behavior.

B. Entity relationship diagram.

C. Data dictionary.

D. Integrity review:
- Missing constraints.
- Duplicate data risks.
- Incorrect relationship cardinality.
- Unnecessary duplication.
- Orphaned records.
- Missing indexes.
- Auditability concerns.
- Transaction and concurrency risks.

E. Proposed schema changes:
- Exact proposed migration.
- Reason for the change.
- Existing-data compatibility.
- Migration and rollback considerations.
- Impact on existing APIs and frontend code.

Do not generate or execute migrations during the initial analysis.

# 10. PHASE EIGHT — API AND ERROR CONTRACTS

Inventory the existing REST endpoints and compare them with the documented requirements.

For each endpoint, document:
- HTTP method and path.
- Purpose.
- Authentication requirements.
- Permission requirements.
- Path and query parameters.
- Request DTO and field validation.
- Response DTO.
- Success status codes.
- Failure status codes.
- Business conditions.
- Database side effects.
- Transaction behavior.
- Idempotency and concurrency behavior.
- Pagination and filtering.
- Relevant tests.

Inspect existing exception handling and response formats.

Create a centralized error catalog with stable application error codes for cases such as:
- Validation failure.
- Resource not found.
- Duplicate resource.
- Invalid document status.
- Unauthorized operation.
- Missing or inaccessible attachment.
- Duplicate workflow action.
- Concurrent modification.
- Storage failure.
- Unexpected internal error.

Do not introduce new response conventions without assessing the existing frontend dependencies and backward compatibility.

# 11. PHASE NINE — SECURITY AND AUDIT

Evaluate:
- Authentication and session/token handling.
- Password and secret management.
- Role and permission enforcement.
- Department and record-level access.
- Insecure direct object reference risks.
- Upload and download security.
- Path traversal and unsafe file names.
- Untrusted file content.
- SQL injection and unsafe query construction.
- Cross-site scripting and CSRF where relevant.
- CORS and transport security.
- Sensitive information in logs and errors.
- Audit log integrity.
- Administrative privilege boundaries.
- Backup access and recovery security.

For every finding, provide:
- Severity.
- Evidence.
- Impact.
- Reproduction or verification method, where safe.
- Recommended remediation.
- Regression tests.

Do not expose real secrets or execute destructive security tests.

# 12. PHASE TEN — TESTING AND ACCEPTANCE

Review existing automated tests and map them to requirements.

Create a requirements traceability matrix:

Requirement ID | Business rule ID | API or component | Database entities | Test case IDs | Current coverage | Gap

Prepare test cases for:
- Valid and invalid inputs.
- Authentication and authorization.
- Boundary conditions.
- Duplicate submissions.
- Concurrent requests.
- Workflow transitions.
- Document version changes.
- File upload and download failures.
- Database transaction rollback.
- Audit trail completeness.
- Search permissions.
- Archival and retention rules.
- Regression testing.

For every test, include:
- Test ID.
- Preconditions.
- Input data.
- Steps.
- Expected HTTP response.
- Expected database state.
- Expected storage state.
- Expected audit and notification effects.

Do not claim a test passed unless it was actually executed and the result was observed.

# 13. PHASE ELEVEN — SYSTEM ARCHITECTURE AND DEPLOYMENT

Document the current architecture and propose changes only where justified.

Prepare:
- System context diagram.
- Component architecture diagram.
- Frontend-to-backend request flow.
- Database and object-storage interactions.
- Authentication and authorization flow.
- Workflow and notification flow.
- Deployment topology.
- Environment and configuration inventory.
- Backup and restore design.
- Monitoring and logging design.
- Failure recovery procedures.

Assess the existing deployment for Windows Server 2022, including Java runtime, backend service management, frontend hosting, reverse proxy or IIS configuration, PostgreSQL, object storage, HTTPS, network restrictions, backups, and service recovery.

Distinguish verified infrastructure from assumptions and proposed improvements.

# 14. PHASE TWELVE — PRIORITIZED GAP ANALYSIS

Create a prioritized backlog using:
- P0: Critical security, data integrity, or business-blocking defect.
- P1: Essential for the agreed production scope.
- P2: Important improvement.
- P3: Optional enhancement.

For every backlog item, include:
- Issue ID.
- Related requirement.
- Existing evidence.
- Problem and impact.
- Proposed solution.
- Dependencies.
- Risk.
- Estimated complexity: S/M/L/XL.
- Acceptance criteria.
- Required regression tests.

Do not label a feature as missing unless you have checked the relevant implementation areas.

# 15. REQUIRED OUTPUT DOCUMENTS

Create a dedicated directory:

docs/system-design/

Generate these Markdown documents:

1. 00-project-overview.md
2. 01-repository-and-current-state-analysis.md
3. 02-business-analysis-and-scope.md
4. 03-functional-requirements.md
5. 04-non-functional-requirements.md
6. 05-validation-and-business-rules.md
7. 06-document-workflows-and-state-transitions.md
8. 07-roles-permissions-and-security.md
9. 08-database-schema-and-data-dictionary.md
10. 09-api-specification-and-error-catalog.md
11. 10-file-storage-and-version-management.md
12. 11-audit-retention-and-archival.md
13. 12-testing-and-acceptance-criteria.md
14. 13-architecture-and-deployment.md
15. 14-requirements-traceability-matrix.md
16. 15-gap-analysis-and-development-roadmap.md
17. 16-open-questions-and-business-decisions.md

Include Mermaid diagrams where appropriate. Ensure the diagrams represent verified current behavior or are clearly labeled as proposed designs.

# 16. EXECUTION PROCESS

Work in stages.

STAGE A — Inspect:
- Discover the repository.
- Read relevant source code and configuration.
- Map existing functionality and architecture.
- Review existing tests and migrations.
- Identify evidence and unknowns.

STAGE B — Report:
- Summarize the actual system.
- Identify major risks and gaps.
- Create the requirements and design documents.
- Label confirmed facts, recommendations, and open decisions separately.

STAGE C — Validate:
- Check consistency between requirements, workflows, database, APIs, security, and tests.
- Identify contradictions and duplicate rules.
- Ensure every proposed requirement has a testable acceptance criterion.
- Ensure each important business rule has a defined enforcement layer.
- Ensure the traceability matrix connects requirements to tests and implementation evidence.

STAGE D — Present:
- Summarize the findings.
- Highlight critical risks.
- List the most important questions for the business owner.
- Recommend a prioritized implementation plan.

If you encounter an essential business ambiguity, record it in the open-questions document and ask me for clarification when it materially affects the design.

# 17. FINAL QUALITY STANDARD

The result must be detailed enough that another developer, QA engineer, system administrator, or business analyst can understand the existing system and implement the approved improvements without guessing core business rules.

Do not produce generic textbook explanations. Base the documents on actual repository evidence.

Do not start implementing improvements until I review and approve the analysis and proposed changes.

Begin with Stage A. Inspect the project, then continue through the documentation stages without changing the application code. At the end, report which files you created and which areas still need my confirmation.
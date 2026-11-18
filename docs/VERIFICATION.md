# Verification record

Verified on **18 November 2026 (Asia/Kolkata)**, on macOS arm64.

## Automated tests

| Suite | Executed | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: | ---: |
| JUnit 5 / MockMvc | 94 | 94 | 0 | 0 |
| Vitest / React Testing Library | 14 | 14 | 0 | 0 |
| Live HTTP assertions | 52 | 52 | 0 | 0 |

Latest backend command: `JAVA_HOME=/tmp/task-tracker-jdk21/Contents/Home ./mvnw verify` from `backend/`. This exercised Maven Wrapper 3.9.11 and Java 21.0.13.1, producing the runnable Spring Boot 3.5.16 JAR. Test results are generated under `backend/target/surefire-reports/` (ignored build output).

Final frontend commands: `npm run build` and `npm test` from `frontend/`. TypeScript strict checking, including unused-import checking, and Vite 8.3.8 production bundling succeeded. Tests ran on Vitest 5.1.1. Host Node was 26.7.0 and npm was 11.19.0; Node 24+ is the documented requirement.

Backend test coverage includes user/admin authentication, unknown user and incorrect password, unauthenticated reads and mutations, actual CSRF token exchange/rotation, session ID rotation, logout invalidation, cookie replay rejection, owner isolation, cross-owner GET/PATCH/DELETE returning 404, admin updates/deletes, creation ownership/default status, forbidden payload fields, deterministic ordering, status filtering, title/description boundaries, null/empty description clearing, partial updates, malformed IDs, BCrypt storage, and response data minimization. Each backend test clears task storage and creates its own fixtures.

An added regression test initially demonstrated Jackson accepting a numeric title as a string. Explicit string coercion rules fixed that; four numeric/boolean payload regression cases are included in the current 94 passing tests. Earlier failures are not represented as successes.

Frontend tests cover public homepage access, protected-route redirection, invalid credentials feedback, task creation payload restrictions, trimmed-title validation, editing status, and clearing protected UI on session expiration.

## Real HTTP verification

`python3 scripts/verify_http.py` passed all **52 assertions** against the final packaged backend through the isolated Vite proxy at `http://127.0.0.1:5174` (backend port 8081). This is a separate real-network check using Python's standard HTTP/cookie libraries, not MockMvc or frontend mocks.

It verifies all demo logins, user isolation, CRUD, admin operations, validation, filtering, CSRF rejection, logout, and replay of the old session cookie. The script removes its disposable tasks. It can be rerun against a running application.

## Browser verification

Actual in-app browser interaction covered:

- Public homepage and login navigation.
- Alice sign-in and private task list.
- Creating a task through the form, then editing its title and status.
- Filtering to Done; verifying the edited task remains and others disappear.
- Confirming deletion; verifying success feedback and the empty filtered state.
- Signing out and returning to login.
- Admin sign-in; Alice and Bob groups with owner labels.
- Admin editing Alice's task and deleting Bob's seeded demo task.
- Responsive task grouping and controls at **390 × 844** and **1440 × 1000**.
- After restarting the finalized backend, the previous browser session returned to sign-in; admin could sign in again.

The backend was restarted after destructive demo checks, restoring the original seeded tasks. Screenshots of the final state are included as `dashboard-desktop.jpg` and `dashboard-mobile.jpg`. They contain only fictional demo data.

This is not a claim of cross-browser certification or a formal WCAG audit. No automated Playwright suite is checked in; browser checks were performed interactively through the available browser tool.

## Dependency review

- `npm audit --omit=dev`: **0 vulnerabilities**.
- Full `npm audit`: **6 high-severity package findings**, all caused by the same upstream picomatch ReDoS advisory propagating through Vite/Vitest build dependencies (`picomatch`, `fdir`, `tinyglobby`, `vite`, `vitest`, `@vitejs/plugin-react`).
- Advisory: [GHSA-fc5q-39xc-vjpf](https://github.com/advisories/GHSA-fc5q-39xc-vjpf).
- Registry inspection reported **picomatch 4.0.7** as the latest release, still within the affected range. The dependency audit's downgrade suggestions do not provide a compatible fix for the complete current toolchain. No suppression or forced downgrade was used to hide the finding.
- Production React Router and development Vite/Vitest packages were updated to current releases to resolve the other reported advisories. Current build/test patterns are fixed local project inputs; no application endpoint accepts glob patterns. The affected development tools are not shipped in the static browser bundle.
- Keep dev servers loopback-only, do not run untrusted project glob patterns, and update the upstream package once patched. This is a disclosed development-tool limitation, not a clean full-audit claim.
- Maven dependencies were updated through Spring Boot's maintained 3.5.16 BOM; a comprehensive JVM SCA scan was not performed.

## Submission assessment

Mandatory application functionality, tests, setup documentation, demo credentials, and local execution are implemented and verified. Optional registration, pagination, and a server statistics endpoint are omitted. Summary cards derive counts from authorized tasks.

The initial implementation was committed as one large commit. Later changes are separated by purpose; the actual history was preserved. The preference for small commits throughout the initial implementation was therefore not fully met.

This is ready for local take-home review with the disclosed dependency and commit-history caveats. It is not a hardened public production deployment.

## Manager and assignment update

The update adds a MANAGER demo account, leader-only `GET /api/users`, and validated `assignee` support on POST/PATCH. New tests cover both admin and manager assignment, safe directory responses, regular-user rejection, invalid targets, preservation of other task fields, loss/gain of access after reassignment, manager CRUD, repeated login without hash erasure, dropdown loading/error/retry behavior, and immediate owner-group updates.

Additional browser verification on the isolated preview: manager login routed to `/manager`; the create form displayed admin/alice/bob/manager options with roles; a task assigned to Alice appeared under Alice; editing its assignee to Bob moved it to Bob’s group; logging in as Bob showed the task in his private workspace. Screenshot: `task-assignment.jpg`.

The existing user-owned servers on ports 8080/5173 were not stopped or restarted; their in-memory tasks were preserved. Only the isolated verification servers are cleaned up. Restart the normal backend to load the new role and endpoints. This necessarily resets in-memory tasks/sessions, as documented.

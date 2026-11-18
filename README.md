# Task Tracker

A complete local task-management application with a public homepage, secure sign-in, private user workspaces, and an administrator overview grouped by task owner. Users can create, edit, filter, and delete tasks; administrators can manage every user's tasks. All data lives in memory and resets when the backend restarts.

## Quick start

Prerequisites: **Java 21**, **Node.js 24 LTS or newer**, and npm. Maven **3.9.11** is downloaded automatically by the included Maven Wrapper; a separate Maven installation is unnecessary. First installation requires internet access.

From the repository root, start the backend in one terminal:

```sh
cd backend
./mvnw spring-boot:run
```

In a second terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open **http://127.0.0.1:5173/**. The backend listens on **127.0.0.1:8080**. Use the frontend URL for the application; Vite proxies `/api` to the backend so cookies and CSRF tokens stay on the same origin. Do not mix `localhost` and `127.0.0.1` in the same session.

On Windows use `mvnw.cmd` instead of `./mvnw`. Check `java -version` and set `JAVA_HOME` to your Java 21 installation if needed. During implementation, Java 21 was downloaded temporarily to `/tmp/task-tracker-jdk21/Contents/Home`; on this machine it can be selected with `export JAVA_HOME=/tmp/task-tracker-jdk21/Contents/Home` while that directory exists. Install a permanent JDK for ongoing use.

The repository is not published. Once hosted, clone its actual URL with `git clone <repository-url> task-tracker`, then `cd task-tracker` and use the commands above.

## Local demo credentials

These accounts are public **local demonstration fixtures only**, not real credentials. Do not deploy the demo accounts to an internet-facing environment.

| Username | Password | Role |
| --- | --- | --- |
| `alice` | `Alice-demo-2026!` | USER |
| `bob` | `Bob-demo-2026!` | USER |
| `admin` | `Admin-demo-2026!` | ADMIN |

Startup hashes the demo passwords with BCrypt before storing users in memory. Alice and Bob each receive two example tasks. Newly created tasks belong to their creator, including tasks created by an administrator.

## Stack

| Component | Version |
| --- | --- |
| Java source/bytecode | 21 |
| Verified JDK | Amazon Corretto 21.0.13.1 |
| Spring Boot | 3.5.16 |
| Spring Web / Spring Security | 6.2.19 / 6.5.11 (Boot-managed) |
| Spring Validation | Boot-managed Jakarta Bean Validation |
| Maven Wrapper distribution | 3.9.11 |
| React / React DOM | 19.1.1 |
| React Router DOM | 7.18.5 |
| TypeScript | 5.9.3 |
| Vite / React plugin | 8.3.8 / 6.3.0 |
| Vitest | 5.1.1 |
| React Testing Library / jest-dom | 16.3.0 / 6.9.1 |
| jsdom | 27.0.1 |
| Storage | Java concurrent collections; no database or ORM |

Backend tests use JUnit 5, Spring Boot Test, MockMvc, and Spring Security Test. Frontend network calls use the Fetch API. Dependency versions are pinned in `pom.xml` and `package-lock.json`. Spring Validation's Hibernate **Validator** is a validation library, not Hibernate ORM; there is no JPA or database dependency.

## Tests and builds

From the repository root:

```sh
cd backend
./mvnw clean verify
cd ../frontend
npm ci
npm test
npm run build
```

With both servers running, execute the live HTTP checks from the repository root:

```sh
python3 scripts/verify_http.py
```

Python 3's standard library is sufficient. The script uses real cookies and CSRF headers, creates disposable tasks, verifies both user and admin workflows, and removes its tasks. An alternate base URL can be passed as its first argument.

The backend package is `backend/target/task-tracker-1.0.0.jar`, runnable with `java -jar backend/target/task-tracker-1.0.0.jar`. Frontend build output is `frontend/dist`. A production deployment needs a same-origin static server/reverse proxy with SPA route fallback and `/api` forwarded to the backend. The Vite development server is the documented local execution path, not a production server.

See [verification results](docs/VERIFICATION.md) for actual executed checks and browser coverage.

## API contract

All request/response bodies are JSON except successful `204` responses. Authentication uses the `JSESSIONID` HttpOnly session cookie. No JWT, browser localStorage, or client-controlled roles are used.

| Method | Endpoint | Result |
| --- | --- | --- |
| GET | `/api/csrf` | Public: CSRF token and header name |
| POST | `/api/login` | Public with CSRF: authenticate; return username and role |
| GET | `/api/me` | Current authenticated username and role |
| POST | `/api/logout` | Invalidate session, clear cookie/context; `204` |
| GET | `/api/tasks` | Authorized tasks, ordered by owner then numeric ID |
| GET | `/api/tasks?status=todo` | Authorized tasks matching a valid status |
| GET | `/api/tasks/{id}` | One authorized task; added for explicit by-ID access |
| POST | `/api/tasks` | Create an owned `todo` task; `201` |
| PATCH | `/api/tasks/{id}` | Update supplied title, description, or status; `200` |
| DELETE | `/api/tasks/{id}` | Delete an authorized task; `204` |

Statuses are exactly `todo`, `in_progress`, and `done`. Regular users can see and mutate only their own tasks. Administrators can see and mutate all tasks. Missing and inaccessible task IDs both return `404`.

### Session and CSRF example

These commands require curl and Python 3. Run them together in a shell. Obtain a token before login, then fetch a **fresh token after login**, because authentication rotates both the session ID and CSRF token.

```sh
base=http://127.0.0.1:5173
cookies=$(mktemp)
token=$(curl -fsS -c "$cookies" "$base/api/csrf" | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
curl -fsS -b "$cookies" -c "$cookies" -H 'Content-Type: application/json' \
  -H "X-CSRF-TOKEN: $token" \
  -d '{"username":"alice","password":"Alice-demo-2026!"}' "$base/api/login"
token=$(curl -fsS -b "$cookies" -c "$cookies" "$base/api/csrf" | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
curl -fsS -b "$cookies" -H 'Content-Type: application/json' \
  -H "X-CSRF-TOKEN: $token" \
  -d '{"title":"Prepare the review","description":"Collect feedback"}' "$base/api/tasks"
curl -fsS -b "$cookies" "$base/api/tasks?status=todo"
# Replace 5 with the actual id returned by creation:
curl -fsS -X PATCH -b "$cookies" -H 'Content-Type: application/json' \
  -H "X-CSRF-TOKEN: $token" -d '{"status":"done"}' "$base/api/tasks/5"
curl -fsS -X POST -b "$cookies" -H "X-CSRF-TOKEN: $token" "$base/api/logout"
rm "$cookies"
```

Successful login:

```json
{"username":"alice","role":"USER"}
```

Created task (ID varies):

```json
{"id":5,"owner":"alice","title":"Prepare the review","description":"Collect feedback","status":"todo"}
```

PATCH accepts any nonempty subset of `title`, `description`, and `status`:

```json
{"title":"Review the final draft","description":null,"status":"in_progress"}
```

Omitted fields remain unchanged. `null` or `""` clears the description. Title is trimmed, must contain 3–100 characters, and cannot be blank or null. Description is optional and limited to 500 characters. Status cannot be null or unknown. Validation applies equally to administrators. Unknown fields are rejected, including `owner`, `id`, and `role`; creation rejects **any** client-supplied `status` (even `todo`). This rejects spoofed ownership instead of silently ignoring it.

Errors use an `error` code and a human-readable `message`:

```json
{"error":"NOT_FOUND","message":"Task not found"}
```

| Status | Meaning |
| --- | --- |
| 400 | Invalid JSON, fields, filter, or malformed numeric ID |
| 401 | Missing/expired authentication, invalid credentials, or unauthenticated mutation without CSRF |
| 403 | Authenticated request has a missing/invalid CSRF token |
| 404 | Task does not exist or is not accessible to the current user |

Login uses the same error for unknown usernames and incorrect passwords. Password hashes and internal exception traces are never returned by the application endpoints.

## Architecture

```text
backend/
  pom.xml, mvnw, .mvn/         Reproducible Java build
  src/main/java/com/tasktracker/
    config/                    Spring Security and serialization configuration
    controller/                Authentication and task HTTP endpoints
    dto/                       Login/create request validation
    model/                     Immutable task record
    repository/                Concurrent in-memory task storage and demo fixtures
    service/                   Ownership-aware operations and PATCH validation
    exception/                 Consistent API validation/not-found errors
  src/main/resources/          Session/cookie/server settings
  src/test/                    MockMvc security and contract tests
frontend/
  src/App.tsx                  Session restoration and protected routes
  src/auth.ts                  Shared identity context
  src/api.ts                   Fetch, CSRF token handling, session expiration
  src/Home.tsx, Login.tsx       Public screens
  src/Dashboard.tsx            User/admin list, filtering, feedback, deletion
  src/TaskEditor.tsx            Create/edit forms
  src/Modal.tsx, Brand.tsx      Shared accessible UI
  src/styles.css               Responsive layout and status styles
  src/App.test.tsx              Essential frontend workflow tests
scripts/verify_http.py          Reproducible live HTTP checks
docs/VERIFICATION.md            Verification evidence and known limits
```

Tasks are immutable records stored in a `ConcurrentHashMap`. An `AtomicLong` allocates IDs. Updates/deletes use atomic `compute` operations; authorization is repeated inside the mutation to avoid acting on an unchecked record. Repository snapshots do not expose mutable internal collections. User records are managed by Spring Security's in-memory user manager. Controllers delegate task business rules to the service.

## Security design

- BCrypt hashes are the only password representation retained in the user store. Plaintext demo literals are bootstrap fixtures, documented for reviewers.
- Successful authentication rotates any existing session ID, creates a fresh security context, and explicitly saves it to the HTTP session.
- Spring Security's session-backed CSRF protection remains enabled for login, logout, and every task mutation. `/api/csrf` exposes a masked token; the browser sends it in `X-CSRF-TOKEN`. Login clears the previous token; the client fetches another before its next mutation.
- Cookies are HttpOnly and SameSite=Strict. `Secure` defaults to false solely for loopback HTTP development; set `COOKIE_SECURE=true` behind production HTTPS. Sessions expire after 30 minutes of inactivity.
- Logout invalidates the server-side session, clears the security context and cookie, and discards protected UI state. The frontend handles later `401` responses by returning to sign-in.
- Ownership is assigned from the authenticated principal. Role and owner cannot be changed in task payloads. Backend authorization applies independently of frontend controls.
- Authentication/task responses use Spring Security's default no-cache headers. React renders task text without raw HTML injection. No broad CORS policy is enabled.
- Native dialog elements provide modal semantics and keyboard focus containment. Forms have labels, limits, loading feedback, cancel actions, and error announcements. Delete requires confirmation.

## Assumptions and limitations

- Java and Spring Boot follow the assignment's required stack and provide established session/security/validation support.
- The required in-memory storage intentionally loses data and sessions on restart. It is designed for a single application process, not clustered deployment.
- Session authentication is a good fit for this same-origin app; token persistence and JWT infrastructure would add unnecessary complexity.
- Status filtering and summary cards in the UI derive from the authorized server task list. The required server `status` query parameter is also supported and tested. There is no optional `/api/tasks/stats` endpoint.
- Registration and pagination are not implemented. Only seeded users can sign in. Task lists are unpaginated.
- Demo credentials, lack of rate limiting/account recovery, and in-memory data make this a local assessment application, not an internet production deployment.
- UI typography optionally uses Google Fonts; system fonts are used if it is unreachable.
- Production frontend dependencies audit clean. The full development dependency audit still reports an upstream **picomatch ReDoS advisory** propagated through build/test tools. The latest published picomatch version at verification time is still affected; no safe compatible patch is available. Build/test glob patterns here are fixed repository inputs, not user-supplied strings. Details and mitigation scope are recorded in `docs/VERIFICATION.md`.
- Browser verification covers the in-app browser at desktop and mobile widths, not every browser/device or a formal accessibility audit.

## Git and submission

The project has local commits and no configured publishing destination. The first commit contains the initial implementation as one large change; subsequent refactoring, testing, security/build changes, and documentation are separate commits. This is the actual history, not a reconstructed incremental development history.

Review `git log --oneline` and `git status --short` before submission. To publish later, create an empty repository in your own account, then run:

```sh
git remote add origin <repository-url>
git push -u origin main
```

Nothing has been pushed or published automatically.

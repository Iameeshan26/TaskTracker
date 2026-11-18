# Task Tracker

A full-stack task management web application built with **Spring Boot, React, and TypeScript**. It provides session-based authentication, personal task workspaces, and role-based task management for administrators and managers.

Tasks are stored in memory, as required by the take-home assignment. Task data and sessions reset when the backend restarts.

## Features

- Public homepage with project information and a login link.
- Username/password login and session-based logout.
- Personal task dashboard for regular users.
- Create, edit, delete, and filter tasks by status.
- Administrator and manager dashboards showing tasks grouped by assignee.
- Authorized task assignment and reassignment by administrators and managers.
- Backend-enforced access controls: regular users cannot access another user's tasks.
- Input validation, consistent JSON errors, and automated tests.

### Task statuses

`todo` · `in_progress` · `done`

New tasks start with `todo`.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5.16, Spring Web |
| Authentication and security | Spring Security, BCrypt, HTTP sessions, CSRF protection |
| Frontend | React 19.1.1, TypeScript 5.9.3, Vite 8.3.8, React Router 7.18.5 |
| Storage | In-memory Java concurrent collections |
| Backend testing | JUnit 5, Spring Boot Test, MockMvc, Spring Security Test |
| Frontend testing | Vitest, React Testing Library |
| Build tools | Maven Wrapper, npm |

## Getting Started

### Prerequisites

- Java 21
- Node.js 24 LTS or newer and npm
- Internet access for initial dependency installation

Maven does not need to be installed separately; the project includes a Maven Wrapper.

### 1. Start the backend

From the repository root:

```bash
cd backend
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd spring-boot:run` instead.

The backend runs at `http://127.0.0.1:8080`.

### 2. Start the frontend

Open a second terminal from the repository root:

```bash
cd frontend
npm ci
npm run dev
```

Open **http://127.0.0.1:5173/** in your browser.

The Vite development server proxies `/api` requests to the backend. Use `127.0.0.1` consistently rather than mixing it with `localhost` in the same browser session.

## Demo Credentials

The application seeds the following **local demonstration accounts** at startup:

| Username | Password | Role |
| --- | --- | --- |
| `alice` | `Alice-demo-2026!` | USER |
| `bob` | `Bob-demo-2026!` | USER |
| `admin` | `Admin-demo-2026!` | ADMIN |
| `manager` | `Manager-demo-2026!` | MANAGER |

Passwords are hashed using BCrypt before being stored in memory. Alice and Bob each have two example tasks. These accounts are intended only for local demonstration and must not be deployed as production credentials.

## Roles and Permissions

| Action | USER | ADMIN | MANAGER |
| --- | :---: | :---: | :---: |
| View own tasks | Yes | Yes | Yes |
| Create tasks | Yes | Yes | Yes |
| Edit or delete own tasks | Yes | Yes | Yes |
| View all users' tasks | No | Yes | Yes |
| Edit or delete any task | No | Yes | Yes |
| Assign or reassign tasks | No | Yes | Yes |
| View user directory | No | Yes | Yes |

Regular users work only with their own tasks. Administrators and managers can view tasks grouped by assignee and use the **Assign to** field when creating or editing tasks. Reassignment moves a task to the selected user's workspace.

## API Reference

All API endpoints return JSON unless a successful response has no body (`204 No Content`). Authentication uses an HttpOnly `JSESSIONID` cookie. State-changing requests require a valid CSRF token.

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/csrf` | Retrieve CSRF token and header name |
| `POST` | `/api/login` | Authenticate a user |
| `POST` | `/api/logout` | End the authenticated session (`204`) |
| `GET` | `/api/me` | Retrieve current user information |
| `GET` | `/api/users` | List user summaries (admin/manager only) |
| `GET` | `/api/tasks` | List tasks visible to the current user |
| `GET` | `/api/tasks?status=todo` | Filter visible tasks by status |
| `GET` | `/api/tasks/{id}` | Retrieve an accessible task |
| `POST` | `/api/tasks` | Create a task (`201`) |
| `PATCH` | `/api/tasks/{id}` | Update permitted task fields |
| `DELETE` | `/api/tasks/{id}` | Delete an accessible task (`204`) |

### Task fields and validation

| Field | Rules |
| --- | --- |
| `title` | Required; 3–100 characters after trimming |
| `description` | Optional; maximum 500 characters |
| `status` | `todo`, `in_progress`, or `done` |

Task creation automatically sets `status` to `todo`. A regular user creates tasks for themselves. Administrators and managers can additionally specify an existing account with the `assignee` field.

Example task creation request:

```json
{
  "title": "Prepare project review",
  "description": "Summarize completed tasks"
}
```

Example response:

```json
{
  "id": 5,
  "owner": "alice",
  "title": "Prepare project review",
  "description": "Summarize completed tasks",
  "status": "todo"
}
```

The ID shown above is illustrative and may differ at runtime.

A `PATCH` request can change any nonempty subset of `title`, `description`, and `status`. Omitting a field leaves it unchanged; setting `description` to `null` or an empty string clears it. Admins and managers may also use `assignee` to reassign a task.

Example status update:

```json
{
  "status": "in_progress"
}
```

Example assignment by an admin or manager:

```json
{
  "assignee": "bob"
}
```

### HTTP response codes

| Code | Meaning |
| --- | --- |
| `200` | Successful request with response data |
| `201` | Task created |
| `204` | Successful deletion or logout |
| `400` | Invalid input, malformed ID, or unsupported status |
| `401` | Authentication required or credentials invalid |
| `403` | Invalid/missing CSRF token or insufficient permission for a restricted action |
| `404` | Task not found or not accessible to the current user |

For privacy, an attempt by a regular user to access another user's task returns `404`, just like a nonexistent task.

## Testing

### Backend tests

```bash
cd backend
./mvnw clean verify
```

### Frontend tests and build

```bash
cd frontend
npm ci
npm test
npm run build
```

### Live API verification

With both the backend and frontend running, execute the verification script from the repository root:

```bash
python3 scripts/verify_http.py
```

The script exercises authenticated HTTP requests, CSRF handling, task operations, and role-based access using temporary tasks.

See [Verification Results](docs/VERIFICATION.md) for recorded test results, browser coverage, and known verification limitations.

## Project Structure

```text
TaskTracker/
├── backend/
│   ├── pom.xml
│   ├── mvnw
│   └── src/
│       ├── main/java/com/tasktracker/
│       │   ├── config/
│       │   ├── controller/
│       │   ├── dto/
│       │   ├── exception/
│       │   ├── model/
│       │   ├── repository/
│       │   └── service/
│       └── test/
├── frontend/
│   └── src/
├── scripts/
│   └── verify_http.py
├── docs/
│   └── VERIFICATION.md
└── README.md
```

The backend separates HTTP controllers, business logic, security, validation, and in-memory storage. The frontend separates pages, task editing, authentication state, and API communication.

## Security

- **Password hashing:** BCrypt hashes are stored in the in-memory user directory; plaintext demo passwords are used only as startup fixtures.
- **Authentication:** Spring Security manages authenticated HTTP sessions. Login rotates the session ID; logout invalidates the session and clears the authentication state.
- **CSRF protection:** Login, logout, and task mutations require CSRF tokens. The frontend retrieves and submits the token through the API.
- **Authorization:** Backend checks enforce ownership for regular users and elevated permissions for administrators and managers.
- **Session cookies:** Cookies are HttpOnly and SameSite=Strict. The `Secure` flag can be enabled for HTTPS deployments with `COOKIE_SECURE=true`.
- **API responses:** Password hashes and internal exception traces are not exposed through application endpoints.

## Assumptions and Limitations

- Java and Spring Boot were chosen from the backend technologies permitted by the assignment.
- Data is stored in memory, so tasks and sessions reset on backend restart. The application is designed for a single process rather than a distributed deployment.
- Demo accounts are fixed; self-service registration and account recovery are not implemented.
- Optional pagination and the `/api/tasks/stats` endpoint are not implemented. Dashboard status summaries are derived from the authorized task list.
- The application is intended for local assessment, not public production deployment. Production use would require additional controls, including removal of demo credentials and login rate limiting.
- A development dependency advisory involving `picomatch` is documented in [Verification Results](docs/VERIFICATION.md). Production frontend dependencies were reported clean in the recorded audit; see that document for details and scope.


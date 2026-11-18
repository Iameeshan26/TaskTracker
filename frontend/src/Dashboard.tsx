import {useState,useEffect,useContext} from "react";
import {Link,useNavigate} from "react-router-dom";
import {api} from "./api";
import {statuses,type Task} from "./types";
import {Auth,message} from "./auth";
import Brand from "./Brand";
import Modal from "./Modal";
import TaskEditor from "./TaskEditor";
export default function Dashboard() {
  const { user, setUser } = useContext(Auth);
  const navigate = useNavigate();
  const [tasks, setTasks] = useState<Task[]>([]),
    [filter, setFilter] = useState(""),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [editing, setEditing] = useState<Task | null | undefined>(undefined),
    [deleting, setDeleting] = useState<Task | null>(null),
    [busy, setBusy] = useState(false);
  const admin = user?.role === "ADMIN";
  async function refresh() {
    setLoading(true);
    setError("");
    try {
      setTasks(await api<Task[]>("/tasks"));
    } catch (e) {
      setError(message(e));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void refresh();
  }, []);
  async function logout() {
    setBusy(true);
    try {
      await api("/logout", { method: "POST" });
      setTasks([]);
      setUser(null);
      navigate("/login", { replace: true });
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  async function remove() {
    if (!deleting) return;
    setBusy(true);
    try {
      await api("/tasks/" + deleting.id, { method: "DELETE" });
      setTasks((t) => t.filter((x) => x.id !== deleting.id));
      setDeleting(null);
      setNotice("Task deleted.");
    } catch (e) {
      setError(message(e));
      setDeleting(null);
    } finally {
      setBusy(false);
    }
  }
  const visible = tasks.filter((t) => !filter || t.status === filter);
  const groups = admin
    ? [...new Set(visible.map((t) => t.owner))]
    : [user!.username];
  return (
    <div className="workspace">
      <aside className="sidebar">
        <Brand />
        <div className="workspace-label">WORKSPACE</div>
        <Link className="nav-active" to={admin ? "/admin" : "/dashboard"}>
          <span>▦</span>
          {admin ? "Team tasks" : "My tasks"}
          <span className="nav-count">{tasks.length}</span>
        </Link>
        <div className="sidebar-bottom">
          <div className="sidebar-note">
            <span>One thing at a time.</span>
            <p>
              Small steps make
              <br />
              meaningful progress.
            </p>
            <div>↗</div>
          </div>
          <div className="profile">
            <span className="avatar">{user!.username[0].toUpperCase()}</span>
            <div>
              <strong>{user!.username}</strong>
              <small>{admin ? "Administrator" : "Personal workspace"}</small>
            </div>
          </div>
          <button className="logout" onClick={logout} disabled={busy}>
            Sign out <span>↗</span>
          </button>
        </div>
      </aside>
      <main className="dashboard">
        <header className="workspace-top">
          <span>
            Workspace <span className="slash">/</span>{" "}
            <strong>{admin ? "Team overview" : "My tasks"}</strong>
          </span>
          <span className="private-label">
            <span className="tiny-dot" />
            {admin ? "Admin workspace" : "Your private workspace"}
          </span>
        </header>
        <section className="dashboard-heading">
          <div>
            <span className="eyebrow">LET’S MAKE SOME PROGRESS</span>
            <h1>{admin ? "The bigger picture." : "Your work, in focus."}</h1>
            <p>
              {admin
                ? "A little clarity across the team. Every task, organized by owner."
                : `Welcome back, ${user!.username}. A clear mind starts with a clear plan.`}
            </p>
          </div>
          <button onClick={() => setEditing(null)}>＋ Create task</button>
        </section>
        <section className="stats" aria-label="Task summary">
          <div>
            <span>Total tasks</span>
            <strong>{tasks.length.toString().padStart(2, "0")}</strong>
            <small>Everything in your workspace</small>
          </div>
          {Object.entries(statuses).map(([key, label]) => (
            <div key={key}>
              <span>
                <i className={"dot " + key} />
                {label}
              </span>
              <strong>
                {tasks
                  .filter((t) => t.status === key)
                  .length.toString()
                  .padStart(2, "0")}
              </strong>
              <small>
                {key === "todo"
                  ? "Ready when you are"
                  : key === "in_progress"
                    ? "Moving things forward"
                    : "A little win worth celebrating"}
              </small>
            </div>
          ))}
        </section>
        <section className="task-section">
          <div className="list-toolbar">
            <h2>
              {admin ? "All team tasks" : "Your tasks"}{" "}
              <span>{visible.length}</span>
            </h2>
            <div className="filters" aria-label="Filter tasks by status">
              {[["", "All tasks"], ...Object.entries(statuses)].map(
                ([key, label]) => (
                  <button
                    key={key}
                    aria-pressed={filter === key}
                    className={filter === key ? "selected" : ""}
                    onClick={() => setFilter(key)}
                  >
                    {label}
                  </button>
                ),
              )}
            </div>
          </div>
          {notice && (
            <div className="notice" role="status">
              ✓ {notice}
              <button
                aria-label="Dismiss notification"
                onClick={() => setNotice("")}
              >
                ×
              </button>
            </div>
          )}
          {error && (
            <div className="error" role="alert">
              {error}{" "}
              <button className="text-button" onClick={refresh}>
                Try again
              </button>
            </div>
          )}
          {loading ? (
            <div className="empty" role="status">
              Gathering your tasks…
            </div>
          ) : !visible.length ? (
            <div className="empty">
              <span className="empty-icon">✓</span>
              <h3>
                {filter
                  ? "Nothing here just yet."
                  : "A little space for your next idea."}
              </h3>
              <p>
                {filter
                  ? "Try another status or create something new."
                  : "Add your first task and take it one step at a time."}
              </p>
              <button className="secondary" onClick={() => setEditing(null)}>
                ＋ Create task
              </button>
            </div>
          ) : (
            groups.map((owner) => (
              <section key={owner} className="owner-group">
                {admin && (
                  <h3>
                    <span className="avatar small">
                      {owner[0].toUpperCase()}
                    </span>
                    {owner}
                    <small>
                      {visible.filter((t) => t.owner === owner).length} tasks
                    </small>
                  </h3>
                )}
                <div className="task-table">
                  <div className="table-head">
                    <span>TASK</span>
                    <span>STATUS</span>
                    <span>ACTIONS</span>
                  </div>
                  {visible
                    .filter((t) => t.owner === owner)
                    .map((task) => (
                      <article className="task-row" key={task.id}>
                        <div className="task-main">
                          <span className={"task-mark " + task.status}>
                            {task.status === "done"
                              ? "✓"
                              : task.status === "in_progress"
                                ? "◔"
                                : "○"}
                          </span>
                          <div>
                            <h3>{task.title}</h3>
                            <p>{task.description || "No description added."}</p>
                          </div>
                        </div>
                        <span className={"status " + task.status}>
                          <i />
                          {statuses[task.status]}
                        </span>
                        <div className="actions">
                          <button
                            aria-label={"Edit " + task.title}
                            title="Edit task"
                            onClick={() => setEditing(task)}
                          >
                            Edit
                          </button>
                          <button
                            className="delete-action"
                            aria-label={"Delete " + task.title}
                            title="Delete task"
                            onClick={() => setDeleting(task)}
                          >
                            Delete
                          </button>
                        </div>
                      </article>
                    ))}
                </div>
              </section>
            ))
          )}
        </section>
        <div className="dashboard-foot">
          A little progress, every day.{" "}
          <span>
            Task Tracker / {admin ? "Team workspace" : "Personal workspace"}
          </span>
        </div>
      </main>
      {editing !== undefined && (
        <TaskEditor
          task={editing}
          onClose={() => setEditing(undefined)}
          onSave={(t) => {
            setTasks((old) =>
              editing
                ? old.map((x) => (x.id === t.id ? t : x))
                : [...old, t].sort(
                    (a, b) => a.owner.localeCompare(b.owner) || a.id - b.id,
                  ),
            );
            setEditing(undefined);
            setNotice(
              editing ? "Task updated." : "Task created. Ready when you are.",
            );
          }}
        />
      )}
      {deleting && (
        <Modal
          title="Delete this task?"
          onClose={() => !busy && setDeleting(null)}
        >
          <p className="modal-description">
            “{deleting.title}” will be permanently removed. This cannot be
            undone.
          </p>
          <div className="form-actions">
            <button
              className="secondary"
              disabled={busy}
              onClick={() => setDeleting(null)}
            >
              Keep task
            </button>
            <button className="danger" disabled={busy} onClick={remove}>
              {busy ? "Deleting…" : "Delete task"}
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}

import {useState,type FormEvent} from "react";
import {api} from "./api";
import {statuses,type Task,type Status} from "./types";
import {message} from "./auth";
import Modal from "./Modal";
export default function TaskEditor({
  task,
  onClose,
  onSave,
}: {
  task: Task | null;
  onClose: () => void;
  onSave: (task: Task) => void;
}) {
  const [title, setTitle] = useState(task?.title || ""),
    [description, setDescription] = useState(task?.description || ""),
    [status, setStatus] = useState<Status>(task?.status || "todo"),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (title.trim().length < 3) {
      setError("Title must contain at least 3 non-padding characters.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      onSave(
        await api<Task>(task ? "/tasks/" + task.id : "/tasks", {
          method: task ? "PATCH" : "POST",
          body: JSON.stringify({
            title: title.trim(),
            description,
            ...(task ? { status } : {}),
          }),
        }),
      );
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={task ? "A little refinement." : "Make space for a new task."}
      onClose={() => !busy && onClose()}
    >
      <p className="modal-description">
        {task
          ? "Keep the details clear and the progress moving."
          : "Every bit of progress starts with a first step."}
      </p>
      <form onSubmit={submit}>
        <label htmlFor="task-title">
          Title <span className="required">*</span>
        </label>
        <input
          id="task-title"
          autoFocus
          required
          minLength={3}
          maxLength={100}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="What would you like to work on?"
        />
        <label htmlFor="task-description">
          Description <span className="optional">Optional</span>
        </label>
        <textarea
          id="task-description"
          rows={4}
          maxLength={500}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Add a little context…"
        />
        <small className="char-count">{description.length}/500</small>
        {task ? (
          <>
            <label htmlFor="task-status">Status</label>
            <select
              id="task-status"
              value={status}
              onChange={(e) => setStatus(e.target.value as Status)}
            >
              {Object.entries(statuses).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </>
        ) : (
          <p className="creation-note">
            <span className="status todo">
              <i />
              To do
            </span>{" "}
            New tasks start here. The rest is up to you.
          </p>
        )}
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <div className="form-actions">
          <button
            type="button"
            className="secondary"
            disabled={busy}
            onClick={onClose}
          >
            Cancel
          </button>
          <button disabled={busy}>
            {busy ? "Saving…" : task ? "Save changes" : "Create task"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

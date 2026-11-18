import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { vi, it, expect, afterEach } from "vitest";
import TaskEditor from "./TaskEditor";
import App from "./App";
import { Auth } from "./auth";
import { api } from "./api";
import type { User, Task } from "./types";
vi.mock("./api", async () => ({
  ...(await vi.importActual<typeof import("./api")>("./api")),
  api: vi.fn(),
}));
const mock = vi.mocked(api);
const accounts: User[] = [
  { username: "alice", role: "USER" },
  { username: "bob", role: "USER" },
  { username: "manager", role: "MANAGER" },
  { username: "admin", role: "ADMIN" },
];
const task: Task = {
  id: 1,
  owner: "alice",
  title: "Assigned task",
  description: "Context",
  status: "todo",
};
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
function editor(role: User["role"], existing: Task | null = null) {
  const saved = vi.fn();
  render(
    <Auth.Provider
      value={{
        user: { username: role.toLowerCase(), role },
        setUser: () => {},
      }}
    >
      <TaskEditor task={existing} onClose={() => {}} onSave={saved} />
    </Auth.Provider>,
  );
  return saved;
}
it.each(["ADMIN", "MANAGER"] as const)(
  "%s can select a real user when creating a task",
  async (role) => {
    mock.mockImplementation(async (path) =>
      path === "/users" ? accounts : task,
    );
    const saved = editor(role);
    expect(screen.getByRole("button", { name: "Create task" })).toBeDisabled();
    await screen.findByRole("option", { name: "alice · User" });
    fireEvent.change(screen.getByLabelText("Assign to"), {
      target: { value: "alice" },
    });
    fireEvent.change(screen.getByLabelText(/Title/), {
      target: { value: "Assigned task" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create task" }));
    await waitFor(() => expect(saved).toHaveBeenCalledWith(task));
    expect(mock).toHaveBeenCalledWith("/tasks", {
      method: "POST",
      body: JSON.stringify({
        title: "Assigned task",
        description: "",
        assignee: "alice",
      }),
    });
  },
);
it("preselects current assignee and submits reassignment on edit", async () => {
  mock.mockImplementation(async (path) =>
    path === "/users" ? accounts : { ...task, owner: "bob" },
  );
  const saved = editor("MANAGER", task);
  await screen.findByRole("option", { name: "bob · User" });
  expect(screen.getByLabelText("Assign to")).toHaveValue("alice");
  fireEvent.change(screen.getByLabelText("Assign to"), {
    target: { value: "bob" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
  await waitFor(() =>
    expect(saved).toHaveBeenCalledWith({ ...task, owner: "bob" }),
  );
  expect(mock).toHaveBeenCalledWith("/tasks/1", {
    method: "PATCH",
    body: JSON.stringify({
      title: "Assigned task",
      description: "Context",
      status: "todo",
      assignee: "bob",
    }),
  });
});
it("does not fetch or show the user directory for regular users", () => {
  editor("USER", task);
  expect(screen.queryByLabelText("Assign to")).not.toBeInTheDocument();
  expect(mock).not.toHaveBeenCalled();
});
it("blocks saving when directory loading fails and lets the user retry", async () => {
  mock
    .mockRejectedValueOnce(new Error("Network unavailable"))
    .mockResolvedValue(accounts);
  editor("MANAGER");
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Could not load users",
  );
  expect(screen.getByRole("button", { name: "Create task" })).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "Retry" }));
  await screen.findByRole("option", { name: "alice · User" });
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Create task" })).toBeEnabled();
});
it("shows a team workspace and owner groups for a manager", async () => {
  mock.mockImplementation(async (path) =>
    path === "/me"
      ? { username: "manager", role: "MANAGER" }
      : [task, { ...task, id: 2, owner: "bob", title: "Bob task" }],
  );
  render(
    <MemoryRouter initialEntries={["/manager"]}>
      <App />
    </MemoryRouter>,
  );
  expect(await screen.findByText("Assigned task")).toBeInTheDocument();
  expect(screen.getByText("Bob task")).toBeInTheDocument();
  expect(screen.getByText("Manager workspace")).toBeInTheDocument();
  expect(
    screen.getByRole("heading", { name: /All team tasks/ }),
  ).toBeInTheDocument();
});
it("moves a reassigned task into the new owner group without reloading", async () => {
  mock.mockImplementation(async (path) =>
    path === "/me"
      ? { username: "manager", role: "MANAGER" }
      : path === "/users"
        ? accounts
        : path === "/tasks/1"
          ? { ...task, owner: "bob" }
          : [task],
  );
  render(
    <MemoryRouter initialEntries={["/manager"]}>
      <App />
    </MemoryRouter>,
  );
  fireEvent.click(
    await screen.findByRole("button", { name: "Edit Assigned task" }),
  );
  await screen.findByRole("option", { name: "bob · User" });
  fireEvent.change(screen.getByLabelText("Assign to"), {
    target: { value: "bob" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
  expect(await screen.findByRole("status")).toHaveTextContent("Task updated");
  expect(
    screen.getByRole("heading", { name: "Tasks assigned to bob" }),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("heading", { name: "Tasks assigned to alice" }),
  ).not.toBeInTheDocument();
});

import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { vi, describe, it, expect, afterEach, beforeEach } from "vitest";
import App from "./App";
import TaskEditor from "./TaskEditor";
import { api, ApiError } from "./api";
vi.mock("./api", async () => {
  const actual = await vi.importActual<typeof import("./api")>("./api");
  return { ...actual, api: vi.fn() };
});
const mock = vi.mocked(api);
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
beforeEach(() => {
  mock.mockRejectedValue(new ApiError(401, "Please sign in"));
});
describe("essential workflows", () => {
  it("shows public homepage without authentication", async () => {
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByText(/Make room/)).toBeInTheDocument();
    expect(screen.getByText("In progress")).toBeInTheDocument();
  });
  it("redirects protected routes to sign in", async () => {
    render(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByLabelText("Username")).toBeInTheDocument();
  });
  it("displays invalid credentials", async () => {
    render(
      <MemoryRouter initialEntries={["/login"]}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByLabelText("Username");
    mock.mockRejectedValue(new ApiError(401, "Invalid username or password"));
    fireEvent.change(screen.getByLabelText("Username"), {
      target: { value: "alice" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "bad" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Sign in/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Invalid username or password",
    );
  });
  it("creates without sending status or ownership", async () => {
    mock.mockResolvedValue({
      id: 8,
      owner: "alice",
      title: "New task",
      description: "",
      status: "todo",
    });
    const saved = vi.fn();
    render(<TaskEditor task={null} onClose={() => {}} onSave={saved} />);
    fireEvent.change(screen.getByLabelText(/Title/), {
      target: { value: "New task" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create task" }));
    await waitFor(() => expect(saved).toHaveBeenCalled());
    expect(mock).toHaveBeenCalledWith("/tasks", {
      method: "POST",
      body: JSON.stringify({ title: "New task", description: "" }),
    });
  });
  it("rejects whitespace padded short titles", async () => {
    render(<TaskEditor task={null} onClose={() => {}} onSave={() => {}} />);
    fireEvent.change(screen.getByLabelText(/Title/), {
      target: { value: " a " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create task" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("at least 3");
    expect(mock).not.toHaveBeenCalled();
  });
  it("edits status with existing fields", async () => {
    const task = {
      id: 3,
      owner: "alice",
      title: "Current title",
      description: "Context",
      status: "todo" as const,
    };
    mock.mockResolvedValue({ ...task, status: "done" });
    const saved = vi.fn();
    render(<TaskEditor task={task} onClose={() => {}} onSave={saved} />);
    fireEvent.change(screen.getByLabelText("Status"), {
      target: { value: "done" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(saved).toHaveBeenCalled());
    expect(mock).toHaveBeenCalledWith("/tasks/3", {
      method: "PATCH",
      body: JSON.stringify({
        title: "Current title",
        description: "Context",
        status: "done",
      }),
    });
  });
  it("clears protected dashboard on session expiration", async () => {
    mock.mockImplementation(async (path) =>
      path === "/me"
        ? { username: "alice", role: "USER" }
        : [
            {
              id: 1,
              owner: "alice",
              title: "Private task",
              description: "Secret",
              status: "todo",
            },
          ],
    );
    render(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByText("Private task")).toBeInTheDocument();
    window.dispatchEvent(new Event("session-expired"));
    expect(await screen.findByLabelText("Username")).toBeInTheDocument();
    expect(screen.queryByText("Private task")).not.toBeInTheDocument();
  });
});

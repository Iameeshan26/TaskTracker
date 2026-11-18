export type Status = "todo" | "in_progress" | "done";
export type User = { username: string; role: "USER" | "ADMIN" | "MANAGER" };
export type Task = {
  id: number;
  owner: string;
  title: string;
  description: string | null;
  status: Status;
};
export const statuses: Record<Status, string> = {
  todo: "To do",
  in_progress: "In progress",
  done: "Done",
};

export const canManageTasks = (user: User | null) =>
  user?.role === "ADMIN" || user?.role === "MANAGER";
export const workspacePath = (user: User) =>
  user.role === "ADMIN"
    ? "/admin"
    : user.role === "MANAGER"
      ? "/manager"
      : "/dashboard";

import { createContext } from "react";
import type { User } from "./types";
export const Auth = createContext<{
  user: User | null;
  setUser: (user: User | null) => void;
}>({ user: null, setUser: () => {} });
export const message = (e: unknown) =>
  e instanceof Error ? e.message : "Something went wrong. Please try again.";

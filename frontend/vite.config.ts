import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "TASK_TRACKER_");
  return {
    plugins: [react()],
    server: {
      proxy: { "/api": env.TASK_TRACKER_API_URL || "http://127.0.0.1:8080" },
    },
    test: { environment: "jsdom", setupFiles: ["./src/test-setup.ts"] },
  };
});

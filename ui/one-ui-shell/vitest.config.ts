import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react() as any],
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    globals: true,
    /** Many page tests drive forms + waitFor BFF mocks; parallel runs need headroom for CI. */
    testTimeout: 30_000,
    hookTimeout: 15_000,
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
      "@registry-templates": path.resolve(__dirname, "../../registry-templates"),
    },
  },
});

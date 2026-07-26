import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globals: true,
    // Co-located component tests (src/**) are included deliberately: the previous
    // pattern matched only test/**/*.test.ts, so a test committed next to its
    // component — or any .tsx test — was silently never executed. A test that
    // does not run is worse than no test: it reports safety that was never checked.
    include: ["test/**/*.test.{ts,tsx}", "src/**/*.test.{ts,tsx}"],
  },
});

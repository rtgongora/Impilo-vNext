import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: {
      /**
       * Resolve the sibling workspace package from source.
       *
       * Without this, `@impilo/mobile-trust` resolves through `node_modules`, which in a git
       * worktree is a symlink into the CANONICAL checkout — so tests silently exercise a
       * different tree's copy of the package than the one being edited. A new export appears
       * to not exist, and an edit appears to have no effect. Metro and the real build resolve
       * the workspace source; this makes vitest agree with them.
       */
      "@impilo/mobile-trust": fileURLToPath(
        new URL("../mobile-trust/src/index.ts", import.meta.url),
      ),
    },
  },
  test: {
    globals: true,
    include: ["test/**/*.test.ts"],
  },
});

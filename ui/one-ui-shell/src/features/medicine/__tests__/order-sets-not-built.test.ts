import { describe, expect, it } from "vitest";
import { readFileSync, readdirSync, statSync } from "fs";
import { join } from "path";
import { AMBULATORY_ORDER_SETS_BUILT } from "@/data/orderSets";

/**
 * EncounterCart / OrderSetPicker must not be mounted as a live feature until an ambulatory
 * order-set SoR exists. This guard fails the build if anything outside the cart folder imports them.
 */

function walkTsFiles(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    const st = statSync(p);
    if (st.isDirectory()) {
      if (name === "node_modules" || name === ".next") continue;
      walkTsFiles(p, out);
    } else if (/\.(tsx?|jsx?)$/.test(name)) {
      out.push(p);
    }
  }
  return out;
}

describe("ambulatory order sets — honest not-built", () => {
  it("declares ambulatory order sets as not built", () => {
    expect(AMBULATORY_ORDER_SETS_BUILT).toBe(false);
  });

  it("does not mount EncounterCart or OrderSetPicker outside the cart folder", () => {
    const root = join(__dirname, "../../..");
    const cartDir = join(root, "components/ehr/cart");
    const offenders: string[] = [];
    for (const file of walkTsFiles(join(root))) {
      if (file.startsWith(cartDir)) continue;
      if (file.includes("/__tests__/") || file.endsWith(".test.ts") || file.endsWith(".test.tsx")) {
        continue;
      }
      const text = readFileSync(file, "utf8");
      if (
        /from\s+["']@\/components\/ehr\/cart/.test(text) ||
        /from\s+["'].*\/ehr\/cart\//.test(text) ||
        /<(EncounterCart|OrderSetPicker)\b/.test(text)
      ) {
        offenders.push(file);
      }
    }
    expect(offenders, offenders.join("\n")).toEqual([]);
  });
});

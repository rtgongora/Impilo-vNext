import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("wallet payment golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("send page uses canonical wallet hooks and BFF pay endpoints", () => {
    const page = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/wallet/send/page.tsx"),
      "utf8",
    );
    const hooks = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useMusheWallet.ts"),
      "utf8",
    );
    expect(page).toContain("useWalletByOwner");
    expect(page).toContain("useTransfer");
    expect(page).toContain("usePayMerchant");
    expect(hooks).toContain("/internal/v1/wallet/pay");
    expect(hooks).toContain("/internal/v1/wallet/me/balance");
  });

  it("BFF wallet controller exposes citizen pay and balance routes", () => {
    const controller = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/WalletController.java",
      ),
      "utf8",
    );
    expect(controller).toContain("@RequestMapping(\"/internal/v1/wallet\")");
    expect(controller).toContain("/pay");
    expect(controller).toContain("/me/balance");
  });
});

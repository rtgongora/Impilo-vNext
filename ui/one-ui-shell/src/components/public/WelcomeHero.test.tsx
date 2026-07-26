import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/lib/api-client";
import { classifyPublicIntent, WelcomeHero } from "./WelcomeHero";

const push = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

vi.mock("@/lib/i18n/useI18n", () => ({
  useI18n: () => ({
    t: (key: string) =>
      ({
        "public.welcome.eyebrow": "Zimbabwe's National Health Operating System",
        "public.welcome.needFirstTitle": "How can Impilo help you today?",
        "public.welcome.needFirstIntro":
          "Find care, access services, get trusted information or work securely.",
        "public.welcome.nompiloPrompt": "Ask anything about your health, services or support.",
      })[key] ?? key,
  }),
}));

const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;

describe("WelcomeHero", () => {
  beforeEach(() => {
    post.mockReset();
    push.mockReset();
  });

  it("starts with the person's need and keeps public and trust-boundary actions visible", () => {
    render(<WelcomeHero />);

    expect(
      screen.getByRole("heading", { name: "How can Impilo help you today?" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Get care" })).toHaveAttribute(
      "href",
      "/welcome/find-care",
    );
    expect(screen.getByRole("link", { name: "Emergency" })).toHaveAttribute(
      "href",
      "/welcome/emergency",
    );
    expect(screen.getByRole("link", { name: "My Impilo" })).toBeInTheDocument();
    // The "you don't need an account" promise moved into the continuity block, which now
    // always renders — previously a first-time visitor saw nothing where it belongs.
    expect(screen.getByTestId("hero-continuity")).toBeInTheDocument();
    expect(screen.getByText(/without an account/i)).toBeInTheDocument();
  });

  it("routes safety-critical intent immediately without waiting for Nompilo", () => {
    render(<WelcomeHero />);

    fireEvent.click(screen.getByRole("button", { name: "There has been an accident" }));

    expect(screen.getByRole("heading", { name: "Start emergency help now" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /open emergency help/i })).toHaveAttribute(
      "href",
      "/welcome/emergency",
    );
    expect(post).not.toHaveBeenCalled();
  });

  it("uses the anonymous guidance endpoint and renders grounded public guidance", async () => {
    post.mockResolvedValue({
      answer: "Use the public directory to compare suitable services.",
      sources: ["National service directory"],
      disclaimer: "General guidance, not a diagnosis.",
    });
    render(<WelcomeHero />);

    fireEvent.change(screen.getByLabelText("Ask Nompilo"), {
      target: { value: "Where can I get help for anxiety?" },
    });
    fireEvent.submit(screen.getByRole("search", { name: /ask nompilo/i }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/public/gateway/guidance/ask", {
        question: "Where can I get help for anxiety?",
      }),
    );
    expect(await screen.findByText(/use the public directory/i)).toBeInTheDocument();
    expect(screen.getByText(/general guidance, not a diagnosis/i)).toBeInTheDocument();
  });

  it("degrades honestly while leaving real public routes available", async () => {
    post.mockRejectedValue({ status: 502 });
    render(<WelcomeHero />);

    fireEvent.change(screen.getByLabelText("Ask Nompilo"), {
      target: { value: "I need a clinic open now" },
    });
    fireEvent.submit(screen.getByRole("search", { name: /ask nompilo/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/temporarily unavailable/i);
    expect(screen.getByRole("link", { name: "Find care" })).toHaveAttribute(
      "href",
      expect.stringContaining("/welcome/find-care"),
    );
  });
});

describe("classifyPublicIntent", () => {
  it.each([
    ["There has been an accident", "/welcome/emergency"],
    ["I want to give feedback", "/welcome/report"],
    ["renew my professional registration", "/welcome/regulatory"],
    ["compare medical aid", "/welcome/coverage"],
    ["show first aid courses", "/welcome/learning"],
    ["find a maternity clinic", "/welcome/find-care?q=find%20a%20maternity%20clinic"],
  ])("maps %s to an existing public journey", (question, expectedHref) => {
    expect(classifyPublicIntent(question)?.href).toBe(expectedHref);
  });
});

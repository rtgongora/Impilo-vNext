import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AddDependant } from "./AddDependant";

const mutateAsync = vi.fn();
const state: { data: unknown; isPending: boolean } = { data: undefined, isPending: false };

vi.mock("@/hooks/queries/useDependants", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useDependants")>(
    "@/hooks/queries/useDependants",
  );
  return {
    ...actual, // keep the real dependantErrorMessage extractor
    useAddDependant: () => ({ ...state, mutateAsync }),
  };
});

function fillForm() {
  fireEvent.click(screen.getByText("Add a dependant"));
  fireEvent.change(screen.getByText("First name").parentElement!.querySelector("input")!, {
    target: { value: "Tinashe" },
  });
  fireEvent.change(screen.getByText("Last name").parentElement!.querySelector("input")!, {
    target: { value: "Moyo" },
  });
  fireEvent.change(screen.getByText("Date of birth").parentElement!.querySelector("input")!, {
    target: { value: "2020-01-01" },
  });
}

describe("AddDependant (CJ14)", () => {
  beforeEach(() => {
    mutateAsync.mockReset();
    state.data = undefined;
    state.isPending = false;
  });

  it("submits the child's details (guardian is server-side, never entered here)", () => {
    render(<AddDependant />);
    fillForm();
    fireEvent.click(screen.getByText("Add dependant"));
    expect(mutateAsync).toHaveBeenCalledWith({
      givenName: "Tinashe",
      familyName: "Moyo",
      dateOfBirth: "2020-01-01",
      sex: undefined,
    });
  });

  it("keeps submit disabled until required fields are present", () => {
    render(<AddDependant />);
    fireEvent.click(screen.getByText("Add a dependant"));
    expect(screen.getByText("Add dependant")).toBeDisabled();
  });

  it("surfaces the server's over-age rejection message (422)", async () => {
    mutateAsync.mockRejectedValueOnce({
      status: 422,
      error: { code: "UNPROCESSABLE_ENTITY", message: "This person is at or over the age of majority and must hold their own account" },
    });
    render(<AddDependant />);
    fillForm();
    fireEvent.click(screen.getByText("Add dependant"));
    await waitFor(() =>
      expect(screen.getByText(/at or over the age of majority/i)).toBeInTheDocument(),
    );
  });

  it("confirms with the guardianship expiry on success", () => {
    state.data = {
      data: {
        status: "CREATED",
        dependant: { givenName: "Tinashe", familyName: "Moyo" },
        guardianship: { type: "GUARDIAN", expiresAt: "2038-01-01" },
      },
    };
    render(<AddDependant />);
    expect(screen.getByText(/Tinashe Moyo has been added/)).toBeInTheDocument();
    expect(screen.getByText(/act for them until/i)).toBeInTheDocument();
  });
});

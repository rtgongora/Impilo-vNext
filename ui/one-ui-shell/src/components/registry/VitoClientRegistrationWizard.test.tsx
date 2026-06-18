import { describe, expect, it, vi, beforeEach } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { VitoClientRegistrationWizard } from "./VitoClientRegistrationWizard";

const postMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post: (...args: unknown[]) => postMock(...args),
  },
}));

describe("VitoClientRegistrationWizard", () => {
  beforeEach(() => {
    postMock.mockReset();
    postMock.mockResolvedValue({
      data: {
        id: "11111111-1111-1111-1111-111111111111",
        type: "patient",
        attributes: {
          givenName: "Tendai",
          middleName: "Ruvarashe",
          familyName: "Moyo",
          email: "tendai.moyo@example.zw",
          preferredLanguage: "en-ZW",
        },
      },
      meta: {},
    });
  });

  it("renders extended demographic fields", () => {
    render(<VitoClientRegistrationWizard onRegistered={vi.fn()} />);
    expect(screen.getByText(/Middle name \(optional\)/i)).toBeInTheDocument();
    expect(screen.getByText(/^Email$/i)).toBeInTheDocument();
    expect(screen.getByText(/Passport reference/i)).toBeInTheDocument();
    expect(screen.getByText(/Emergency contact name/i)).toBeInTheDocument();
  });

  it("includes extended demographics in POST /internal/v1/patients payload", async () => {
    const onRegistered = vi.fn();
    render(<VitoClientRegistrationWizard facilityId="fac-harare-central" onRegistered={onRegistered} />);

    const textInputs = screen.getAllByRole("textbox");

    fireEvent.change(screen.getByText(/Family name/i).closest("div")!.querySelector("input")!, {
      target: { value: "Moyo" },
    });
    fireEvent.change(screen.getByText(/Given name/i).closest("div")!.querySelector("input")!, {
      target: { value: "Tendai" },
    });
    fireEvent.change(screen.getByText(/Middle name/i).closest("div")!.querySelector("input")!, {
      target: { value: "Ruvarashe" },
    });
    fireEvent.change(screen.getByText(/Date of birth/i).closest("div")!.querySelector("input")!, {
      target: { value: "1992-04-07" },
    });
    fireEvent.change(screen.getByText(/^Email$/i).closest("div")!.querySelector("input")!, {
      target: { value: "tendai.moyo@example.zw" },
    });
    fireEvent.change(screen.getByText(/Passport reference/i).closest("div")!.querySelector("input")!, {
      target: { value: "P12345678" },
    });
    fireEvent.change(screen.getByText(/Address line/i).closest("div")!.querySelector("input")!, {
      target: { value: "12 Samora Machel Ave" },
    });
    fireEvent.change(screen.getByText(/^City$/i).closest("div")!.querySelector("input")!, {
      target: { value: "Harare" },
    });
    fireEvent.change(screen.getByText(/Preferred language/i).closest("div")!.querySelector("input")!, {
      target: { value: "en-ZW" },
    });
    fireEvent.change(screen.getByText(/Marital status/i).closest("div")!.querySelector("input")!, {
      target: { value: "MARRIED" },
    });
    fireEvent.change(screen.getByText(/Emergency contact name/i).closest("div")!.querySelector("input")!, {
      target: { value: "Rudo Moyo" },
    });
    fireEvent.change(screen.getByText(/Emergency contact phone/i).closest("div")!.querySelector("input")!, {
      target: { value: "+263772345678" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Create Vito-backed client/i }));

    await waitFor(() => expect(postMock).toHaveBeenCalled());
    const [path, body] = postMock.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe("/internal/v1/patients");
    expect(body.middle_name).toBe("Ruvarashe");
    expect(body.email).toBe("tendai.moyo@example.zw");
    expect(body.passport_reference).toBe("P12345678");
    expect(body.address_line).toBe("12 Samora Machel Ave");
    expect(body.city).toBe("Harare");
    expect(body.preferred_language).toBe("en-ZW");
    expect(body.marital_status).toBe("MARRIED");
    expect(body.emergency_contact_name).toBe("Rudo Moyo");
    expect(body.emergency_contact_phone).toBe("+263772345678");
    expect(onRegistered).toHaveBeenCalled();
  });
});

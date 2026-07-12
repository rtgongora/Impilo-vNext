import { describe, expect, it } from "vitest";
import { extractCartId, extractOrderId, unwrapCommerceData } from "../useCommerceFlow";

describe("useCommerceFlow tolerant unwrap helpers", () => {
  it("unwraps a msika-flow {success,data} envelope to the inner record", () => {
    const cart = unwrapCommerceData({ success: true, data: { cartId: "C1", items: [] } });
    expect(cart?.cartId).toBe("C1");
  });

  it("unwraps a doubly-nested data envelope", () => {
    const order = unwrapCommerceData({ data: { data: { orderId: "O1", status: "PRICED" } } });
    expect(order?.orderId).toBe("O1");
    expect(order?.status).toBe("PRICED");
  });

  it("returns null for non-object / arrays", () => {
    expect(unwrapCommerceData(null)).toBeNull();
    expect(unwrapCommerceData([1, 2, 3])).toBeNull();
    expect(unwrapCommerceData("nope")).toBeNull();
  });

  it("extractCartId reads cartId or id", () => {
    expect(extractCartId({ data: { cartId: "C9" } })).toBe("C9");
    expect(extractCartId({ id: "X", items: [] })).toBe("X");
    expect(extractCartId({ nothing: true })).toBeUndefined();
  });

  it("extractOrderId reads orderId or id (string or number)", () => {
    expect(extractOrderId({ success: true, data: { orderId: "ORD-7" } })).toBe("ORD-7");
    expect(extractOrderId({ id: 42, status: "PENDING" })).toBe("42");
    expect(extractOrderId({})).toBeUndefined();
  });
});

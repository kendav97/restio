import { describe, expect, it } from "vitest";
import { EVENT_SCHEMA_VERSION } from "./index.js";

describe("pos-core", () => {
  it("expone la versión del esquema de eventos", () => {
    expect(EVENT_SCHEMA_VERSION).toBe(1);
  });
});

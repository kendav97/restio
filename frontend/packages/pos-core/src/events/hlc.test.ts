import { describe, expect, it } from "vitest";
import { HlcClock, decodeHlc, encodeHlc } from "./hlc.js";

describe("HlcClock", () => {
  it("codifica con ancho fijo para ordenar como texto", () => {
    const value = encodeHlc({ millis: 1_700_000_000_000, counter: 3, nodeId: "dev-a" });

    expect(value).toBe("001700000000000-00003-dev-a");
    expect(decodeHlc(value)).toEqual({ millis: 1_700_000_000_000, counter: 3, nodeId: "dev-a" });
  });

  it("no retrocede aunque el reloj de pared vaya hacia atrás", () => {
    let now = 1000;
    const clock = new HlcClock("a", () => now);

    const first = clock.tick();
    now = 500;
    const second = clock.tick();
    const third = clock.tick();

    expect(first < second && second < third).toBe(true);
    expect(decodeHlc(third)).toMatchObject({ millis: 1000, counter: 2 });
  });

  it("ordena los eventos locales después de uno remoto adelantado", () => {
    const clock = new HlcClock("a", () => 1000);
    const remote = encodeHlc({ millis: 5000, counter: 7, nodeId: "b" });

    clock.receive(remote);
    const local = clock.tick();

    expect(local > remote).toBe(true);
    expect(decodeHlc(local)).toMatchObject({ millis: 5000, counter: 9 });
  });

  it("rechaza un reloj remoto demasiado adelantado", () => {
    const clock = new HlcClock("a", () => 0);

    expect(() =>
      clock.receive(encodeHlc({ millis: 10 * 60_000, counter: 0, nodeId: "b" })),
    ).toThrow(/too far ahead/);
  });

  it("continúa desde el último valor persistido", () => {
    const clock = new HlcClock("a", () => 100, encodeHlc({ millis: 900, counter: 4, nodeId: "a" }));

    expect(decodeHlc(clock.tick())).toMatchObject({ millis: 900, counter: 5 });
  });
});

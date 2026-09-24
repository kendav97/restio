import { describe, expect, it } from "vitest";
import { ApiError, createApiClient, unwrap } from "./client.js";

function fakeFetch(status: number, body: unknown, seen: Request[] = []): typeof fetch {
  return async (input) => {
    seen.push(input as Request);
    return new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
    });
  };
}

describe("createApiClient", () => {
  it("envía el token y el idioma en cada petición", async () => {
    const seen: Request[] = [];
    const api = createApiClient({
      baseUrl: "http://cloud",
      getAccessToken: () => "abc",
      getLocale: () => "es-AR",
      fetch: fakeFetch(200, { accessToken: "a", refreshToken: "r", expiresIn: 900 }, seen),
    });

    const tokens = await unwrap(
      api.POST("/api/v1/auth/pin-login", { body: { deviceId: 7, deviceToken: "t", pin: "1234" } }),
    );

    expect(tokens.expiresIn).toBe(900);
    expect(seen[0]!.url).toBe("http://cloud/api/v1/auth/pin-login");
    expect(seen[0]!.headers.get("Authorization")).toBe("Bearer abc");
    expect(seen[0]!.headers.get("Accept-Language")).toBe("es-AR");
  });

  it("no envía Authorization sin sesión", async () => {
    const seen: Request[] = [];
    const api = createApiClient({ baseUrl: "http://cloud", fetch: fakeFetch(200, [], seen) });

    await unwrap(api.GET("/api/v1/permissions"));

    expect(seen[0]!.headers.has("Authorization")).toBe(false);
  });

  it("convierte una respuesta problem en ApiError con su código estable", async () => {
    const api = createApiClient({
      baseUrl: "http://cloud",
      fetch: fakeFetch(401, { status: 401, code: "INVALID_CREDENTIALS", detail: "PIN incorrecto" }),
    });

    const error = await unwrap(
      api.POST("/api/v1/auth/pin-login", { body: { deviceId: 7, deviceToken: "t", pin: "0000" } }),
    ).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe("INVALID_CREDENTIALS");
    expect((error as ApiError).status).toBe(401);
    expect((error as ApiError).message).toBe("PIN incorrecto");
  });

  it("usa UNEXPECTED si el error no es un problem", async () => {
    const api = createApiClient({ baseUrl: "http://cloud", fetch: fakeFetch(502, { oops: true }) });

    await expect(unwrap(api.GET("/api/v1/auth/me"))).rejects.toMatchObject({
      code: "UNEXPECTED",
      status: 502,
    });
  });
});

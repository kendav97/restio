import createClient, { type Client, type Middleware } from "openapi-fetch";
import type { paths } from "./schema.js";

/** Body of every error response (RFC 7807 problem with a stable `code`, see restio-api-conventions). */
export interface Problem {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  code: string;
  errors?: { field: string; code: string; message?: string }[];
}

/** Error thrown for non-2xx responses. Screens translate it by `code`, never by `message`. */
export class ApiError extends Error {
  constructor(readonly problem: Problem) {
    super(problem.detail ?? problem.title ?? problem.code);
    this.name = "ApiError";
  }

  get code(): string {
    return this.problem.code;
  }

  get status(): number {
    return this.problem.status;
  }
}

export interface ApiClientOptions {
  baseUrl: string;
  /** Current access token, if any. Called before every request. */
  getAccessToken?: () => string | null | undefined;
  /** Locale for localized error messages (`Accept-Language`). */
  getLocale?: () => string;
  fetch?: typeof globalThis.fetch;
}

export type ApiClient = Client<paths>;

export function createApiClient(options: ApiClientOptions): ApiClient {
  const client = createClient<paths>({
    baseUrl: options.baseUrl,
    ...(options.fetch ? { fetch: options.fetch } : {}),
  });
  const headers: Middleware = {
    onRequest({ request }) {
      const token = options.getAccessToken?.();
      if (token) request.headers.set("Authorization", `Bearer ${token}`);
      const locale = options.getLocale?.();
      if (locale) request.headers.set("Accept-Language", locale);
      return request;
    },
  };
  client.use(headers);
  return client;
}

/**
 * Returns the data of an openapi-fetch result or throws an {@link ApiError}.
 * Bodies that are not problems still become an ApiError with code `UNEXPECTED`.
 */
export async function unwrap<T>(
  call: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  const { data, error, response } = await call;
  if (response.ok) return data as T;
  throw new ApiError(toProblem(error, response.status));
}

function toProblem(body: unknown, status: number): Problem {
  if (body && typeof body === "object" && typeof (body as Problem).code === "string") {
    return { ...(body as Problem), status };
  }
  return { status, code: "UNEXPECTED" };
}

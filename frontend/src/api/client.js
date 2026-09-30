// One place for every request to the backend: adds the login token, sends JSON,
// and turns the backend's error format into an ApiError with message and fieldErrors.

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

export class ApiError extends Error {
  constructor(status, message, fieldErrors = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

let token = null;
let onUnauthorized = () => {};

/** Called by AuthContext when the user logs in or out. */
export function setToken(value) {
  token = value;
}

/** Called by AuthContext: what to do when the backend says the token is no longer valid. */
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

export async function apiFetch(path, { method = 'GET', body, query } = {}) {
  const url = new URL(API_URL + path);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, value);
      }
    }
  }

  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, `Can't reach the bank server at ${API_URL}. Check that the backend is running.`);
  }

  if (response.status === 204) return null;
  const data = await response.json().catch(() => null);

  if (!response.ok) {
    // A 401 on a request that sent a token means the session expired or the token is invalid
    if (response.status === 401 && token) onUnauthorized();
    throw new ApiError(
      response.status,
      data?.message ?? `Request failed with status ${response.status}`,
      data?.fieldErrors ?? {},
    );
  }
  return data;
}

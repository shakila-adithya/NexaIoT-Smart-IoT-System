export const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8081";

function getToken() {
  return (
    sessionStorage.getItem("nexaiot_token") ||
    localStorage.getItem("nexaiot_token")
  );
}

function clearStoredAuth() {
  localStorage.removeItem("nexaiot_token");
  localStorage.removeItem("nexaiot_user");
  sessionStorage.removeItem("nexaiot_token");
  sessionStorage.removeItem("nexaiot_user");
}

function handleUnauthorized() {
  clearStoredAuth();
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event("nexaiot:unauthorized"));
  }
}

async function request(
  path,
  { method = "GET", body, headers = {}, cache, responseType } = {},
) {
  const token = getToken();
  const isFormData = typeof FormData !== "undefined" && body instanceof FormData;

  const res = await fetch(`${API_URL}${path}`, {
    method,
    headers: {
      ...(!isFormData ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    ...(cache ? { cache } : {}),
    body: body ? (isFormData ? body : JSON.stringify(body)) : undefined,
  });

  if (res.status === 401) handleUnauthorized();

  if (responseType === "blob") {
    if (!res.ok) {
      const error = new Error(`Request failed with status ${res.status}`);
      error.status = res.status;
      throw error;
    }
    return res.blob();
  }

  const contentType = res.headers.get("content-type") || "";

  let responseData;

  if (contentType.includes("application/json")) {
    responseData = await res.json();
  } else {
    responseData = await res.text();
  }

  if (!res.ok) {
    let message = `Request failed with status ${res.status}`;

    if (responseData?.message) {
      message = responseData.message;
    } else if (responseData && typeof responseData === "object") {
      message = Object.values(responseData)[0] || message;
    } else if (typeof responseData === "string" && responseData.trim()) {
      message = responseData;
    }

    const error = new Error(message);
    error.status = res.status;
    error.data = responseData;

    throw error;
  }

  return responseData;
}

export const apiClient = {
  get: (path, opts) => request(path, { ...opts, method: "GET" }),
  post: (path, body, opts) => request(path, { ...opts, method: "POST", body }),
  put: (path, body, opts) => request(path, { ...opts, method: "PUT", body }),
  patch: (path, body, opts) => request(path, { ...opts, method: "PATCH", body }),
  delete: (path, opts) => request(path, { ...opts, method: "DELETE" }),
};


export const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

function getToken() {
  return (
    sessionStorage.getItem("nexaiot_token") ||
    localStorage.getItem("nexaiot_token")
  );
}

async function request(
  path,
  { method = "GET", body, headers = {}, cache } = {},
) {
  const token = getToken();

  const res = await fetch(`${API_URL}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    ...(cache ? { cache } : {}),
    body: body ? JSON.stringify(body) : undefined,
  });

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


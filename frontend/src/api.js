/* Shared fetch helpers: session cookie is first-party via the Vite proxy. */

async function readBody(res) {
  return res.json().catch(() => ({}));
}

export async function apiGet(path, { headers } = {}) {
  const res = await fetch(path, { credentials: "include", headers });
  const data = await readBody(res);
  if (!res.ok) {
    const error = new Error(data.error || "تعذّر جلب البيانات");
    error.status = res.status;
    throw error;
  }
  return data;
}

export async function apiSend(path, method, payload, { headers } = {}) {
  const res = await fetch(path, {
    method,
    credentials: "include",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(payload),
  });
  const data = await readBody(res);
  if (!res.ok) {
    const error = new Error(data.error || "تعذّر إكمال الطلب");
    error.status = res.status;
    throw error;
  }
  return data;
}

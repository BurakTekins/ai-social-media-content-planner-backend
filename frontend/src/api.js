const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() || '/api';

export const API_BASE_URL =
  configuredBaseUrl === '/' ? '' : configuredBaseUrl.replace(/\/+$/, '');

export class ApiError extends Error {
  constructor(message, { status = 0, payload = null, method = null, url = null, cause } = {}) {
    super(message, cause ? { cause } : undefined);
    this.name = 'ApiError';
    this.status = status;
    this.payload = payload;
    this.method = method;
    this.url = url;
  }
}

function appendQueryValue(searchParams, key, value) {
  if (value === undefined || value === null || value === '') {
    return;
  }

  if (Array.isArray(value)) {
    value.forEach((item) => appendQueryValue(searchParams, key, item));
    return;
  }

  searchParams.append(key, value instanceof Date ? value.toISOString() : String(value));
}

function buildUrl(path, query) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const searchParams = new URLSearchParams();

  Object.entries(query || {}).forEach(([key, value]) => {
    appendQueryValue(searchParams, key, value);
  });

  const queryString = searchParams.toString();
  return `${API_BASE_URL}${normalizedPath}${queryString ? `?${queryString}` : ''}`;
}

function validationMessages(payload) {
  const errors = payload?.errors ?? payload?.violations;

  if (Array.isArray(errors)) {
    return errors
      .map((error) => {
        if (typeof error === 'string') {
          return error;
        }

        const message = error?.defaultMessage ?? error?.message ?? error?.reason;
        const field = error?.field ?? error?.propertyPath;
        return message ? (field ? `${field}: ${message}` : message) : null;
      })
      .filter(Boolean);
  }

  if (errors && typeof errors === 'object') {
    return Object.entries(errors).flatMap(([field, messages]) => {
      const values = Array.isArray(messages) ? messages : [messages];
      return values.filter(Boolean).map((message) => `${field}: ${message}`);
    });
  }

  return [];
}

function errorMessage(payload, status) {
  if (typeof payload === 'string' && payload.trim()) {
    return payload.trim();
  }

  if (payload && typeof payload === 'object') {
    const messages = validationMessages(payload);
    if (messages.length > 0) {
      return messages.join(' · ');
    }

    const message = payload.detail ?? payload.message ?? payload.error ?? payload.title;
    if (typeof message === 'string' && message.trim()) {
      return message.trim();
    }
  }

  return `İstek başarısız oldu (HTTP ${status}).`;
}

async function readErrorPayload(response) {
  const text = await response.text();
  if (!text.trim()) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    const contentType = response.headers.get('content-type') || '';
    return contentType.includes('text/html') ? null : text;
  }
}

async function readSuccessPayload(response, responseType) {
  if (response.status === 204 || response.status === 205) {
    return null;
  }

  if (responseType === 'blob') {
    return response.blob();
  }

  if (responseType === 'blob-url') {
    return URL.createObjectURL(await response.blob());
  }

  const text = await response.text();
  if (!text.trim()) {
    return null;
  }

  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json') || contentType.includes('+json')) {
    return JSON.parse(text);
  }

  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function request(path, { query, responseType, ...options } = {}) {
  const url = buildUrl(path, query);
  const method = options.method || 'GET';
  const headers = new Headers(options.headers);

  if (!headers.has('Accept')) {
    headers.set('Accept', responseType ? '*/*' : 'application/json');
  }

  let response;
  try {
    response = await fetch(url, { ...options, headers });
  } catch (cause) {
    if (cause?.name === 'AbortError') {
      throw cause;
    }

    throw new ApiError('Sunucuya ulaşılamadı. Backend bağlantısını kontrol edin.', {
      method,
      url,
      cause,
    });
  }

  if (!response.ok) {
    const payload = await readErrorPayload(response);
    throw new ApiError(errorMessage(payload, response.status), {
      status: response.status,
      payload,
      method,
      url,
    });
  }

  return readSuccessPayload(response, responseType);
}

function jsonRequest(path, { body, headers, ...options } = {}) {
  return request(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

function segment(value) {
  return encodeURIComponent(String(value));
}

export const api = {
  getPlatforms() {
    return request('/platforms');
  },

  getAiModels(filters = {}) {
    return request('/ai-models', { query: filters });
  },

  listBatches(filters = {}) {
    return request('/generation-batches', { query: filters });
  },

  getBatch(id) {
    return request(`/generation-batches/${segment(id)}`);
  },

  createBatch(payload, files = []) {
    const formData = new FormData();
    formData.append(
      'request',
      new Blob([JSON.stringify(payload)], { type: 'application/json' }),
    );

    Array.from(files || []).filter(Boolean).forEach((file) => {
      formData.append('files', file);
    });

    return request('/generation-batches', {
      method: 'POST',
      body: formData,
    });
  },

  retryBatch(id) {
    return request(`/generation-batches/${segment(id)}/retry`, {
      method: 'POST',
    });
  },

  listContents(filters = {}) {
    return request('/contents', { query: filters });
  },

  getCalendar(filters = {}) {
    return request('/contents/calendar', { query: filters });
  },

  getContent(id) {
    return request(`/contents/${segment(id)}`);
  },

  updateContent(id, payload) {
    return jsonRequest(`/contents/${segment(id)}`, {
      method: 'PATCH',
      body: payload,
    });
  },

  deleteContent(id) {
    return request(`/contents/${segment(id)}`, { method: 'DELETE' });
  },

  replaceMedia(id, type, file) {
    const formData = new FormData();
    formData.append('file', file);
    return request(`/contents/${segment(id)}/media/${segment(type)}`, {
      method: 'PUT',
      body: formData,
    });
  },

  deleteMedia(id, type) {
    return request(`/contents/${segment(id)}/media/${segment(type)}`, {
      method: 'DELETE',
    });
  },

  mediaUrl(id, type) {
    return buildUrl(`/contents/${segment(id)}/media/${segment(type)}/file`);
  },

  scheduleContent(id, scheduledAt) {
    return jsonRequest(`/contents/${segment(id)}/schedule`, {
      method: 'PUT',
      body: { scheduledAt },
    });
  },

  cancelSchedule(id) {
    return request(`/contents/${segment(id)}/schedule`, { method: 'DELETE' });
  },

  getPublishAttempts(id) {
    return request(`/contents/${segment(id)}/publish-attempts`);
  },

  listCredentials() {
    return request('/credentials');
  },

  createCredential(payload) {
    return jsonRequest('/credentials', { method: 'POST', body: payload });
  },

  updateCredentialAccount(id, accountIdentifier) {
    return jsonRequest(`/credentials/${segment(id)}/account-identifier`, {
      method: 'PATCH',
      body: { accountIdentifier },
    });
  },

  rotateCredentialTokens(id, payload) {
    return jsonRequest(`/credentials/${segment(id)}/tokens`, {
      method: 'PUT',
      body: payload,
    });
  },

  setCredentialActive(id, active) {
    return jsonRequest(`/credentials/${segment(id)}/active`, {
      method: 'PATCH',
      body: { active },
    });
  },

  deleteCredential(id) {
    return request(`/credentials/${segment(id)}`, { method: 'DELETE' });
  },
};

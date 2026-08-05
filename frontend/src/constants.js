export const PLATFORM_LABELS = {
  LINKEDIN: 'LinkedIn',
  INSTAGRAM: 'Instagram',
  TWITTER: 'X / Twitter',
};

export const CONTENT_TYPE_LABELS = {
  POST: 'Gönderi',
  REEL: 'Reel',
  TWEET: 'Tweet',
};

export const MEDIA_TYPE_LABELS = {
  IMAGE: 'Görsel',
  VIDEO: 'Video',
};

export const CREDENTIAL_TYPE_LABELS = {
  AI_PROVIDER: 'Yapay zekâ sağlayıcısı',
  SOCIAL_PLATFORM: 'Sosyal medya platformu',
};

export const AI_CAPABILITY_LABELS = {
  TEXT: 'Metin',
  IMAGE: 'Görsel',
  VIDEO: 'Video',
};

export const SOURCE_TYPE_LABELS = {
  LINK: 'Bağlantı',
  DOCUMENT: 'Belge',
};

export const CONTENT_STATUS_META = {
  DRAFT: {
    label: 'Taslak',
    tone: 'slate',
    className: 'border-slate-200 bg-slate-100 text-slate-700',
    calendarClassName: 'border-slate-400 bg-slate-100 text-slate-700',
  },
  SCHEDULED: {
    label: 'Planlandı',
    tone: 'sky',
    className: 'border-sky-200 bg-sky-50 text-sky-700',
    calendarClassName: 'border-sky-500 bg-sky-50 text-sky-700',
  },
  PUBLISHED: {
    label: 'Yayınlandı',
    tone: 'emerald',
    className: 'border-emerald-200 bg-emerald-50 text-emerald-700',
    calendarClassName: 'border-emerald-500 bg-emerald-50 text-emerald-700',
  },
  FAILED: {
    label: 'Başarısız',
    tone: 'rose',
    className: 'border-rose-200 bg-rose-50 text-rose-700',
    calendarClassName: 'border-rose-500 bg-rose-50 text-rose-700',
  },
  DEFAULT: {
    label: 'Bilinmiyor',
    tone: 'slate',
    className: 'border-slate-200 bg-slate-100 text-slate-700',
    calendarClassName: 'border-slate-400 bg-slate-100 text-slate-700',
  },
};

export const BATCH_STATUS_META = {
  IN_PROGRESS: {
    label: 'İşleniyor',
    tone: 'amber',
    className: 'border-amber-200 bg-amber-50 text-amber-700',
  },
  COMPLETED: {
    label: 'Tamamlandı',
    tone: 'emerald',
    className: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  },
  FAILED: {
    label: 'Başarısız',
    tone: 'rose',
    className: 'border-rose-200 bg-rose-50 text-rose-700',
  },
  DEFAULT: {
    label: 'Bilinmiyor',
    tone: 'slate',
    className: 'border-slate-200 bg-slate-100 text-slate-700',
  },
};

export const SOURCE_STATUS_META = {
  PENDING: {
    label: 'Bekliyor',
    tone: 'slate',
    className: 'border-slate-200 bg-slate-100 text-slate-700',
  },
  PROCESSING: {
    label: 'İşleniyor',
    tone: 'amber',
    className: 'border-amber-200 bg-amber-50 text-amber-700',
  },
  COMPLETED: {
    label: 'Tamamlandı',
    tone: 'emerald',
    className: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  },
  FAILED: {
    label: 'Başarısız',
    tone: 'rose',
    className: 'border-rose-200 bg-rose-50 text-rose-700',
  },
  DEFAULT: {
    label: 'Bilinmiyor',
    tone: 'slate',
    className: 'border-slate-200 bg-slate-100 text-slate-700',
  },
};

const dateTimeFormatter = new Intl.DateTimeFormat('tr-TR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

const dateFormatter = new Intl.DateTimeFormat('tr-TR', {
  dateStyle: 'medium',
});

function asValidDate(value) {
  if (!value) {
    return null;
  }

  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDateTime(value) {
  const date = asValidDate(value);
  return date ? dateTimeFormatter.format(date) : '—';
}

export function formatDate(value) {
  const date = asValidDate(value);
  return date ? dateFormatter.format(date) : '—';
}

export function toDateTimeLocal(value) {
  const date = asValidDate(value);
  if (!date) {
    return '';
  }

  const pad = (part) => String(part).padStart(2, '0');
  return [
    date.getFullYear(),
    '-',
    pad(date.getMonth() + 1),
    '-',
    pad(date.getDate()),
    'T',
    pad(date.getHours()),
    ':',
    pad(date.getMinutes()),
  ].join('');
}

export function toIsoFromLocal(value) {
  const date = asValidDate(value);
  return date ? date.toISOString() : null;
}

export function normalizeHashtags(value) {
  const entries = Array.isArray(value) ? value : String(value || '').split(/[\s,]+/);
  const seen = new Set();

  return entries.reduce((hashtags, entry) => {
    const normalized = String(entry || '').trim().replace(/^#+/, '');
    const key = normalized.toLocaleLowerCase('tr-TR');

    if (normalized && !seen.has(key)) {
      seen.add(key);
      hashtags.push(`#${normalized}`);
    }

    return hashtags;
  }, []);
}

export function truncate(value, length = 120) {
  const text = String(value || '');
  if (text.length <= length) {
    return text;
  }

  return `${text.slice(0, Math.max(0, length - 1)).trimEnd()}…`;
}

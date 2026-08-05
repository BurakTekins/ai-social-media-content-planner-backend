import { useCallback, useEffect, useMemo, useState } from 'react';
import { API_BASE_URL } from './api';
import BatchesPage from './pages/BatchesPage';
import ContentsPage from './pages/ContentsPage';
import CalendarPage from './pages/CalendarPage';
import CredentialsPage from './pages/CredentialsPage';

const routes = {
  batches: {
    label: 'Üretim',
    short: 'Üretim',
    icon: '✦',
    component: BatchesPage,
  },
  contents: {
    label: 'İçerikler',
    short: 'İçerik',
    icon: '▤',
    component: ContentsPage,
  },
  calendar: {
    label: 'Takvim',
    short: 'Takvim',
    icon: '□',
    component: CalendarPage,
  },
  credentials: {
    label: 'Bağlantılar',
    short: 'Bağlantı',
    icon: '⌁',
    component: CredentialsPage,
  },
};

function currentRoute() {
  const hash = window.location.hash.replace(/^#\/?/, '').split('/')[0];
  return routes[hash] ? hash : 'batches';
}

function ThemeToggle({ theme, onToggle, compact = false }) {
  const dark = theme === 'dark';

  return (
    <button
      type="button"
      className={`inline-flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white font-bold text-slate-600 shadow-sm transition hover:border-indigo-200 hover:text-indigo-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:hover:border-indigo-500 dark:hover:text-indigo-300 ${compact ? 'h-9 w-9' : 'min-h-11 w-full px-3 text-xs'}`}
      onClick={onToggle}
      aria-label={dark ? 'Açık moda geç' : 'Karanlık moda geç'}
      aria-pressed={dark}
    >
      <span aria-hidden="true">{dark ? '☀' : '☾'}</span>
      {!compact && <span>{dark ? 'Açık moda geç' : 'Karanlık moda geç'}</span>}
    </button>
  );
}

export default function App() {
  const [route, setRoute] = useState(currentRoute);
  const [toast, setToast] = useState(null);
  const [theme, setTheme] = useState(() => document.documentElement.classList.contains('dark') ? 'dark' : 'light');
  const ActivePage = routes[route].component;
  const apiLabel = useMemo(() => API_BASE_URL || '/api', []);

  useEffect(() => {
    const onHashChange = () => setRoute(currentRoute());
    window.addEventListener('hashchange', onHashChange);
    if (!window.location.hash) window.location.hash = '#/batches';
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  useEffect(() => {
    if (!toast) return undefined;
    const timeout = window.setTimeout(() => setToast(null), 4200);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  useEffect(() => {
    const dark = theme === 'dark';
    document.documentElement.classList.toggle('dark', dark);
    document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
    localStorage.setItem('social-plan-theme', theme);
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#070b14' : '#0f172a');
  }, [theme]);

  const notify = useCallback((message, type = 'success') => {
    setToast({ message, type, id: Date.now() });
  }, []);

  return (
    <div className="min-h-screen bg-[#f7f8fc] text-slate-800 transition-colors dark:bg-slate-950 dark:text-slate-200">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r border-slate-200/80 bg-white px-4 py-5 transition-colors dark:border-slate-800 dark:bg-slate-900 lg:flex">
        <a href="#/batches" className="flex items-center gap-3 px-2">
          <span className="grid h-11 w-11 place-items-center rounded-2xl bg-slate-950 text-lg font-bold text-white shadow-lg shadow-slate-950/15">S</span>
          <span>
            <span className="block text-sm font-extrabold tracking-tight text-slate-950">Sosyal Plan</span>
            <span className="text-xs font-medium text-slate-400">İçerik operasyonu</span>
          </span>
        </a>

        <nav className="mt-10 space-y-1.5">
          <p className="px-3 pb-2 text-[10px] font-bold uppercase tracking-[0.2em] text-slate-400">Çalışma alanı</p>
          {Object.entries(routes).map(([key, item]) => (
            <a
              key={key}
              href={`#/${key}`}
              className={`group flex items-center gap-3 rounded-xl px-3 py-3 text-sm font-semibold transition ${
                route === key
                  ? 'bg-slate-950 text-white shadow-sm dark:bg-indigo-600'
                  : 'text-slate-500 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-white'
              }`}
            >
              <span className={`grid h-7 w-7 place-items-center rounded-lg text-sm ${route === key ? 'bg-white/10' : 'bg-slate-100 group-hover:bg-white'}`}>{item.icon}</span>
              {item.label}
            </a>
          ))}
        </nav>

        <div className="mt-auto space-y-3">
          <ThemeToggle theme={theme} onToggle={() => setTheme((current) => current === 'dark' ? 'light' : 'dark')} />
          <div className="rounded-2xl border border-indigo-100 bg-indigo-50/70 p-4 dark:border-indigo-900 dark:bg-indigo-950/50">
            <div className="flex items-center gap-2 text-xs font-bold text-indigo-800 dark:text-indigo-300">
              <span className="h-2 w-2 rounded-full bg-emerald-500" />
              Mock moda hazır
            </div>
            <p className="mt-2 break-all text-[11px] leading-5 text-indigo-600 dark:text-indigo-400">API: {apiLabel}</p>
          </div>
        </div>
      </aside>

      <header className="sticky top-0 z-20 border-b border-slate-200/80 bg-white/90 px-4 py-3 backdrop-blur transition-colors dark:border-slate-800 dark:bg-slate-900/90 lg:hidden">
        <div className="flex items-center justify-between">
          <a href="#/batches" className="flex items-center gap-2 text-sm font-extrabold text-slate-950">
            <span className="grid h-9 w-9 place-items-center rounded-xl bg-slate-950 text-white">S</span>
            Sosyal Plan
          </a>
          <div className="flex items-center gap-2">
            <span className="hidden rounded-full bg-indigo-50 px-3 py-1.5 text-[11px] font-bold text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300 min-[390px]:inline-flex">Mock uyumlu</span>
            <ThemeToggle compact theme={theme} onToggle={() => setTheme((current) => current === 'dark' ? 'light' : 'dark')} />
          </div>
        </div>
      </header>

      <main className="pb-24 lg:ml-64 lg:pb-10">
        <div className="mx-auto max-w-[1500px] px-4 py-7 sm:px-7 lg:px-10 lg:py-10">
          <ActivePage notify={notify} />
        </div>
      </main>

      <nav className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-4 border-t border-slate-200 bg-white/95 px-2 pb-[max(0.5rem,env(safe-area-inset-bottom))] pt-2 shadow-[0_-8px_30px_rgba(15,23,42,0.06)] backdrop-blur transition-colors dark:border-slate-800 dark:bg-slate-900/95 lg:hidden">
        {Object.entries(routes).map(([key, item]) => (
          <a
            key={key}
            href={`#/${key}`}
            className={`flex flex-col items-center gap-1 rounded-xl py-1.5 text-[10px] font-bold transition ${route === key ? 'text-indigo-700 dark:text-indigo-300' : 'text-slate-400 dark:text-slate-500'}`}
          >
            <span className={`grid h-7 w-9 place-items-center rounded-lg text-sm ${route === key ? 'bg-indigo-50' : ''}`}>{item.icon}</span>
            {item.short}
          </a>
        ))}
      </nav>

      {toast && (
        <div
          className={`fixed right-4 top-20 z-[70] max-w-sm rounded-2xl border px-4 py-3 text-sm font-semibold shadow-xl sm:right-7 lg:top-6 ${
            toast.type === 'error'
              ? 'border-rose-100 bg-rose-50 text-rose-700'
              : 'border-emerald-100 bg-white text-slate-800 dark:border-emerald-900 dark:bg-slate-800 dark:text-slate-100'
          }`}
          role="status"
        >
          <span className={`mr-2 ${toast.type === 'error' ? 'text-rose-500' : 'text-emerald-500'}`}>{toast.type === 'error' ? '!' : '✓'}</span>
          {toast.message}
        </div>
      )}
    </div>
  );
}

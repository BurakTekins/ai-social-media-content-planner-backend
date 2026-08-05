import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import {
  CONTENT_STATUS_META,
  CONTENT_TYPE_LABELS,
  PLATFORM_LABELS,
  formatDateTime,
  toDateTimeLocal,
  toIsoFromLocal,
  truncate,
} from '../constants';
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Field,
  Input,
  Modal,
  PageHeader,
  Select,
  Spinner,
  StatusBadge,
} from '../components/ui';

const monthFormatter = new Intl.DateTimeFormat('tr-TR', { month: 'long', year: 'numeric' });
const dayFormatter = new Intl.DateTimeFormat('tr-TR', { weekday: 'short', day: 'numeric', month: 'short' });
const timeFormatter = new Intl.DateTimeFormat('tr-TR', { hour: '2-digit', minute: '2-digit' });
const weekDays = ['Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cmt', 'Paz'];

function startOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function addMonths(date, amount) {
  return new Date(date.getFullYear(), date.getMonth() + amount, 1);
}

function calendarDays(month) {
  const first = startOfMonth(month);
  const offset = (first.getDay() + 6) % 7;
  const start = new Date(first);
  start.setDate(first.getDate() - offset);
  return Array.from({ length: 42 }, (_, index) => {
    const day = new Date(start);
    day.setDate(start.getDate() + index);
    return day;
  });
}

function dateKey(value) {
  const date = value instanceof Date ? value : new Date(value);
  const pad = (part) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function sameDay(left, right) {
  return dateKey(left) === dateKey(right);
}

function ScheduleEditor({ content, initialDay, notify, onSaved }) {
  const baseDate = content.scheduledAt ? new Date(content.scheduledAt) : initialDay || new Date(Date.now() + 15 * 60 * 1000);
  if (!content.scheduledAt && initialDay) {
    baseDate.setHours(10, 0, 0, 0);
    if (baseDate <= new Date()) baseDate.setTime(Date.now() + 15 * 60 * 1000);
  }
  const [value, setValue] = useState(toDateTimeLocal(baseDate));
  const [busy, setBusy] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      const updated = await api.scheduleContent(content.id, toIsoFromLocal(value));
      notify(content.status === 'FAILED' ? 'İçerik yeniden yayın kuyruğuna alındı.' : 'Takvim güncellendi.');
      onSaved(updated);
    } catch (error) {
      notify(error.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit}>
      <div className="mb-5 rounded-2xl bg-slate-50 p-4">
        <div className="flex flex-wrap items-center gap-2"><StatusBadge label={PLATFORM_LABELS[content.platform]} tone="slate" dot={false} /><StatusBadge label={CONTENT_TYPE_LABELS[content.contentType]} tone="slate" dot={false} /></div>
        <p className="mt-3 text-sm font-semibold leading-6 text-slate-700">{truncate(content.text, 180)}</p>
        {content.failureReason && <p className="mt-3 rounded-xl bg-rose-50 p-3 text-xs leading-5 text-rose-700">{content.failureReason}</p>}
      </div>
      <Field label="Yayın tarihi ve saati" hint="Gelecekte bir zaman seçin" required>
        <Input type="datetime-local" min={toDateTimeLocal(new Date())} value={value} required onChange={(event) => setValue(event.target.value)} />
      </Field>
      <div className="mt-6 flex justify-end"><Button type="submit" disabled={busy}>{busy ? 'Kaydediliyor…' : content.status === 'FAILED' ? 'Yeniden dene' : content.status === 'SCHEDULED' ? 'Yeniden planla' : 'Takvime ekle'}</Button></div>
    </form>
  );
}

export default function CalendarPage({ notify }) {
  const [month, setMonth] = useState(startOfMonth(new Date()));
  const [platforms, setPlatforms] = useState([]);
  const [filters, setFilters] = useState({ status: '', platform: '' });
  const [events, setEvents] = useState([]);
  const [drafts, setDrafts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState(null);
  const [selectedDay, setSelectedDay] = useState(null);
  const [cancelBusy, setCancelBusy] = useState(false);

  const range = useMemo(() => ({ from: month.toISOString(), to: addMonths(month, 1).toISOString() }), [month]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [calendarItems, draftPage] = await Promise.all([
        api.getCalendar({ ...range, ...filters }),
        api.listContents({ status: 'DRAFT', platform: filters.platform, page: 0, size: 100 }),
      ]);
      setEvents(calendarItems);
      setDrafts(draftPage.items || []);
      setError(null);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setLoading(false);
    }
  }, [filters, range]);

  useEffect(() => { api.getPlatforms().then(setPlatforms).catch(() => {}); }, []);
  useEffect(() => { load(); }, [load]);

  const days = useMemo(() => calendarDays(month), [month]);
  const eventsByDay = useMemo(() => events.reduce((result, event) => {
    const key = dateKey(event.scheduledAt);
    result[key] = [...(result[key] || []), event];
    return result;
  }, {}), [events]);

  const sortedEvents = useMemo(() => [...events].sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt)), [events]);

  const openEvent = (event, day = null) => {
    if (event.status === 'PUBLISHED') {
      window.location.hash = '#/contents';
      return;
    }
    setSelected(event);
    setSelectedDay(day);
  };

  const changed = () => {
    setSelected(null);
    setSelectedDay(null);
    load();
  };

  const cancel = async () => {
    setCancelBusy(true);
    try {
      await api.cancelSchedule(selected.id);
      notify('Yayın planı iptal edildi; içerik taslağa döndü.');
      changed();
    } catch (cancelError) {
      notify(cancelError.message, 'error');
    } finally {
      setCancelBusy(false);
    }
  };

  return (
    <>
      <PageHeader eyebrow="Yayın planı" title="İçerik takvimi" description="Taslakları yayın sırasına alın; planlanmış içerikleri taşıyın, iptal edin veya başarısız yayınları yeniden deneyin." />

      <div className="grid gap-5 xl:grid-cols-[1fr_320px]">
        <Card className="overflow-hidden">
          <div className="flex flex-col gap-4 border-b border-slate-100 p-5 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-2">
              <Button variant="secondary" size="sm" onClick={() => setMonth(addMonths(month, -1))}>←</Button>
              <Button variant="secondary" size="sm" onClick={() => setMonth(startOfMonth(new Date()))}>Bugün</Button>
              <Button variant="secondary" size="sm" onClick={() => setMonth(addMonths(month, 1))}>→</Button>
              <h2 className="ml-2 text-lg font-extrabold capitalize text-slate-900">{monthFormatter.format(month)}</h2>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <Select value={filters.status} onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}>
                <option value="">Tüm durumlar</option>
                <option value="SCHEDULED">Planlandı</option><option value="PUBLISHED">Yayınlandı</option><option value="FAILED">Başarısız</option>
              </Select>
              <Select value={filters.platform} onChange={(event) => setFilters((current) => ({ ...current, platform: event.target.value }))}>
                <option value="">Tüm platformlar</option>
                {platforms.map((item) => <option key={item.platform} value={item.platform}>{PLATFORM_LABELS[item.platform]}</option>)}
              </Select>
            </div>
          </div>

          {loading && <Spinner label="Takvim yükleniyor" />}
          {!loading && error && <div className="p-5"><ErrorState error={error} onRetry={load} /></div>}
          {!loading && !error && (
            <>
              <div className="hidden grid-cols-7 border-b border-slate-100 bg-slate-50/70 md:grid">{weekDays.map((day) => <div key={day} className="px-3 py-2 text-center text-[10px] font-extrabold uppercase tracking-wider text-slate-400">{day}</div>)}</div>
              <div className="hidden grid-cols-7 md:grid">
                {days.map((day) => {
                  const dayEvents = eventsByDay[dateKey(day)] || [];
                  const inMonth = day.getMonth() === month.getMonth();
                  const today = sameDay(day, new Date());
                  return (
                    <div key={day.toISOString()} className={`min-h-32 border-b border-r border-slate-100 p-2 ${inMonth ? 'bg-white' : 'bg-slate-50/50'}`}>
                      <div className="mb-2 flex items-center justify-between">
                        <span className={`grid h-7 w-7 place-items-center rounded-full text-xs font-bold ${today ? 'bg-indigo-600 text-white' : inMonth ? 'text-slate-600' : 'text-slate-300'}`}>{day.getDate()}</span>
                      </div>
                      <div className="space-y-1.5">
                        {dayEvents.slice(0, 3).map((event) => {
                          const meta = CONTENT_STATUS_META[event.status] || CONTENT_STATUS_META.DEFAULT;
                          return <button key={event.id} onClick={() => openEvent(event, day)} className={`block w-full truncate rounded-lg border-l-2 px-2 py-1.5 text-left text-[10px] font-bold transition hover:brightness-95 ${meta.calendarClassName}`}>{timeFormatter.format(new Date(event.scheduledAt))} · {PLATFORM_LABELS[event.platform]}</button>;
                        })}
                        {dayEvents.length > 3 && <p className="px-2 text-[10px] font-bold text-slate-400">+{dayEvents.length - 3} içerik</p>}
                      </div>
                    </div>
                  );
                })}
              </div>

              <div className="space-y-3 p-4 md:hidden">
                {!sortedEvents.length && <EmptyState title="Bu ay için yayın yok" description="Taslaklardan birini seçerek planlamaya başlayın." />}
                {sortedEvents.map((event) => {
                  const meta = CONTENT_STATUS_META[event.status] || CONTENT_STATUS_META.DEFAULT;
                  return (
                    <button key={event.id} onClick={() => openEvent(event)} className="flex w-full items-center gap-3 rounded-2xl border border-slate-100 p-3 text-left">
                      <div className="min-w-16 rounded-xl bg-slate-50 p-2 text-center"><p className="text-[10px] font-bold capitalize text-slate-400">{dayFormatter.format(new Date(event.scheduledAt)).split(' ')[0]}</p><p className="text-lg font-extrabold text-slate-800">{new Date(event.scheduledAt).getDate()}</p><p className="text-[10px] font-bold text-slate-400">{timeFormatter.format(new Date(event.scheduledAt))}</p></div>
                      <div className="min-w-0"><StatusBadge label={meta.label} tone={meta.tone} /><p className="mt-2 truncate text-sm font-bold text-slate-700">{PLATFORM_LABELS[event.platform]} · {CONTENT_TYPE_LABELS[event.contentType]}</p></div>
                    </button>
                  );
                })}
              </div>
            </>
          )}
        </Card>

        <Card className="h-fit p-5 xl:sticky xl:top-8">
          <div className="flex items-center justify-between"><div><p className="eyebrow">Planlanmamış</p><h2 className="mt-1 font-bold text-slate-900">Taslak kuyruğu</h2></div><span className="grid h-9 min-w-9 place-items-center rounded-full bg-slate-100 px-2 text-sm font-extrabold text-slate-700">{drafts.length}</span></div>
          <p className="mt-2 text-xs leading-5 text-slate-400">Bir taslağı seçerek yayın tarihi belirleyin.</p>
          <div className="mt-5 max-h-[620px] space-y-2 overflow-y-auto pr-1">
            {!drafts.length && !loading && <EmptyState title="Taslak yok" description="Üretim tamamlandığında taslaklar burada görünür." />}
            {drafts.map((draft) => (
              <button key={draft.id} onClick={() => { setSelected(draft); setSelectedDay(null); }} className="w-full rounded-xl border border-slate-100 p-3 text-left transition hover:border-indigo-200 hover:bg-indigo-50/30">
                <div className="flex items-center justify-between gap-2"><span className="text-[10px] font-extrabold uppercase tracking-wide text-indigo-500">{PLATFORM_LABELS[draft.platform]}</span><span className="text-[10px] font-bold text-slate-400">{CONTENT_TYPE_LABELS[draft.contentType]}</span></div>
                <p className="mt-2 text-xs font-semibold leading-5 text-slate-600">{truncate(draft.text, 95)}</p>
              </button>
            ))}
          </div>
        </Card>
      </div>

      <Modal open={Boolean(selected)} title={selected?.status === 'FAILED' ? 'Yayınlamayı yeniden dene' : selected?.status === 'SCHEDULED' ? 'Yayını yeniden planla' : 'Taslağı planla'} description={selected ? `${PLATFORM_LABELS[selected.platform]} · ${CONTENT_TYPE_LABELS[selected.contentType]}` : ''} onClose={() => { setSelected(null); setSelectedDay(null); }} size="md">
        {selected && <>
          <ScheduleEditor content={selected} initialDay={selectedDay} notify={notify} onSaved={changed} />
          {selected.status === 'SCHEDULED' && <div className="mt-4 flex justify-end border-t border-slate-100 pt-4"><Button variant="danger" disabled={cancelBusy} onClick={cancel}>{cancelBusy ? 'İptal ediliyor…' : 'Yayın planını iptal et'}</Button></div>}
        </>}
      </Modal>
    </>
  );
}

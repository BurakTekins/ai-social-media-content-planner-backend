import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import {
  CONTENT_STATUS_META,
  CONTENT_TYPE_LABELS,
  MEDIA_TYPE_LABELS,
  PLATFORM_LABELS,
  formatDateTime,
  normalizeHashtags,
  toDateTimeLocal,
  toIsoFromLocal,
  truncate,
} from '../constants';
import {
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Field,
  Input,
  Modal,
  PageHeader,
  Pagination,
  Select,
  Spinner,
  StatusBadge,
  Textarea,
} from '../components/ui';

function MediaPreview({ content, media }) {
  const source = media.resourcePath
    ? api.mediaUrl(content.id, media.mediaType)
    : media.publicUrl;
  const isMock = source?.startsWith('mock://');

  if (!source || isMock) {
    return (
      <div className="flex aspect-video min-h-36 flex-col items-center justify-center rounded-xl bg-gradient-to-br from-slate-100 to-indigo-50 px-4 text-center">
        <span className="text-2xl">{media.mediaType === 'IMAGE' ? '▧' : '▷'}</span>
        <p className="mt-2 text-xs font-bold text-slate-600">{isMock ? 'Mock medya üretildi' : 'Önizleme bulunmuyor'}</p>
        {isMock && <p className="mt-1 max-w-xs break-all text-[10px] text-slate-400">{source}</p>}
      </div>
    );
  }

  if (media.mediaType === 'VIDEO') {
    return <video className="aspect-video w-full rounded-xl bg-slate-950 object-contain" src={source} controls preload="metadata" />;
  }
  return <img className="aspect-video w-full rounded-xl bg-slate-100 object-contain" src={source} alt="İçerik medyası" />;
}

function ScheduleForm({ content, onSaved, notify }) {
  const defaultValue = content.scheduledAt
    ? toDateTimeLocal(content.scheduledAt)
    : toDateTimeLocal(new Date(Date.now() + 10 * 60 * 1000).toISOString());
  const [scheduledAt, setScheduledAt] = useState(defaultValue);
  const [busy, setBusy] = useState(false);
  const action = content.status === 'FAILED' ? 'Yeniden dene' : content.status === 'SCHEDULED' ? 'Tarihi güncelle' : 'Planla';

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      const updated = await api.scheduleContent(content.id, toIsoFromLocal(scheduledAt));
      notify(content.status === 'FAILED' ? 'İçerik yeniden yayın kuyruğuna alındı.' : 'Yayın zamanı kaydedildi.');
      onSaved(updated);
    } catch (error) {
      notify(error.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit}>
      {content.status === 'FAILED' && (
        <div className="mb-5 rounded-2xl border border-rose-100 bg-rose-50 p-4 text-sm text-rose-700">
          <p className="font-bold">Son yayınlama başarısız</p>
          <p className="mt-1 leading-6">{content.failureReason || 'Hata ayrıntısı bulunmuyor.'}</p>
        </div>
      )}
      <Field label="Yayın tarihi ve saati" hint="Yerel saat diliminiz" required>
        <Input type="datetime-local" required min={toDateTimeLocal(new Date().toISOString())} value={scheduledAt} onChange={(event) => setScheduledAt(event.target.value)} />
      </Field>
      <div className="mt-6 flex justify-end"><Button type="submit" disabled={busy}>{busy ? 'Kaydediliyor…' : action}</Button></div>
    </form>
  );
}

function Attempts({ attempts, loading }) {
  if (loading) return <Spinner label="Yayın denemeleri yükleniyor" />;
  if (!attempts.length) return <EmptyState title="Yayın denemesi yok" description="İçerik yayın job'u tarafından işlendiğinde denemeler burada görünecek." />;
  return (
    <div className="space-y-3">
      {attempts.map((attempt) => (
        <div key={attempt.id} className={`rounded-2xl border p-4 ${attempt.success ? 'border-emerald-100 bg-emerald-50/50' : 'border-rose-100 bg-rose-50/50'}`}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <StatusBadge label={attempt.success ? 'Başarılı' : 'Başarısız'} tone={attempt.success ? 'emerald' : 'rose'} />
            <span className="text-xs font-semibold text-slate-400">{formatDateTime(attempt.attemptedAt)}</span>
          </div>
          {attempt.externalPostId && <p className="mt-3 break-all text-xs font-medium text-emerald-700">Dış gönderi ID: {attempt.externalPostId}</p>}
          {attempt.errorMessage && <p className="mt-3 text-sm leading-6 text-rose-700">{attempt.errorMessage}</p>}
        </div>
      ))}
    </div>
  );
}

function ContentDetail({ content, attempts, attemptsLoading, onChanged, onDelete, notify }) {
  const [tab, setTab] = useState('content');
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(content.text);
  const [hashtags, setHashtags] = useState((content.hashtags || []).join(' '));
  const [busy, setBusy] = useState(false);
  const [mediaBusy, setMediaBusy] = useState('');
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const statusMeta = CONTENT_STATUS_META[content.status] || CONTENT_STATUS_META.DEFAULT;
  const isDraft = content.status === 'DRAFT';

  useEffect(() => {
    setText(content.text);
    setHashtags((content.hashtags || []).join(' '));
  }, [content]);

  const save = async () => {
    setBusy(true);
    try {
      const updated = await api.updateContent(content.id, { text, hashtags: normalizeHashtags(hashtags) });
      notify('Taslak güncellendi.');
      setEditing(false);
      onChanged(updated);
    } catch (error) {
      notify(error.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  const uploadMedia = async (mediaType, file) => {
    if (!file) return;
    setMediaBusy(mediaType);
    try {
      const updated = await api.replaceMedia(content.id, mediaType, file);
      notify(`${MEDIA_TYPE_LABELS[mediaType]} kaydedildi.`);
      onChanged(updated);
    } catch (error) {
      notify(error.message, 'error');
    } finally {
      setMediaBusy('');
    }
  };

  const removeMedia = async (mediaType) => {
    setMediaBusy(mediaType);
    try {
      const updated = await api.deleteMedia(content.id, mediaType);
      notify(`${MEDIA_TYPE_LABELS[mediaType]} silindi.`);
      onChanged(updated);
    } catch (error) {
      notify(error.message, 'error');
    } finally {
      setMediaBusy('');
    }
  };

  const cancelSchedule = async () => {
    setBusy(true);
    try {
      const updated = await api.cancelSchedule(content.id);
      notify('Yayın planı iptal edildi.');
      onChanged(updated);
    } catch (error) {
      notify(error.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  const mediaByType = (type) => content.media?.find((item) => item.mediaType === type);

  return (
    <>
      <div className="mb-5 flex flex-wrap items-center gap-2">
        <StatusBadge label={statusMeta.label} tone={statusMeta.tone} />
        <StatusBadge label={PLATFORM_LABELS[content.platform] || content.platform} tone="slate" dot={false} />
        <StatusBadge label={CONTENT_TYPE_LABELS[content.contentType] || content.contentType} tone="slate" dot={false} />
        {content.textProvider && <span className="text-xs font-semibold text-slate-400">{content.textProvider} · {content.textModel}</span>}
      </div>

      <div className="mb-6 flex gap-1 rounded-xl bg-slate-100 p-1">
        {[['content', 'İçerik'], ['attempts', `Yayın geçmişi (${attempts.length})`]].map(([key, label]) => (
          <button key={key} onClick={() => setTab(key)} className={`flex-1 rounded-lg px-3 py-2 text-xs font-bold transition ${tab === key ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500'}`}>{label}</button>
        ))}
      </div>

      {tab === 'attempts' ? <Attempts attempts={attempts} loading={attemptsLoading} /> : (
        <div className="space-y-7">
          <div>
            <div className="mb-3 flex items-center justify-between"><h3 className="text-sm font-bold text-slate-800">Metin ve hashtag</h3>{isDraft && !editing && <Button size="sm" variant="soft" onClick={() => setEditing(true)}>Düzenle</Button>}</div>
            {editing ? (
              <div className="space-y-4 rounded-2xl bg-slate-50 p-4">
                <Field label="İçerik metni" required><Textarea className="min-h-44" value={text} onChange={(event) => setText(event.target.value)} /></Field>
                <Field label="Hashtag'ler" hint="Boşluk, virgül veya satır ile ayırın"><Textarea className="min-h-20" value={hashtags} onChange={(event) => setHashtags(event.target.value)} /></Field>
                <div className="flex justify-end gap-2"><Button variant="secondary" size="sm" onClick={() => setEditing(false)}>Vazgeç</Button><Button size="sm" onClick={save} disabled={busy || !text.trim()}>{busy ? 'Kaydediliyor…' : 'Kaydet'}</Button></div>
              </div>
            ) : (
              <div className="rounded-2xl bg-slate-50 p-5">
                <p className="whitespace-pre-wrap text-sm leading-7 text-slate-700">{content.text}</p>
                <div className="mt-4 flex flex-wrap gap-2">{content.hashtags?.map((tag) => <span key={tag} className="rounded-full bg-white px-3 py-1.5 text-xs font-bold text-indigo-600 shadow-sm">{tag}</span>)}</div>
              </div>
            )}
          </div>

          <div>
            <h3 className="mb-3 text-sm font-bold text-slate-800">Medya</h3>
            <div className="grid gap-4 sm:grid-cols-2">
              {['IMAGE', 'VIDEO'].map((mediaType) => {
                const media = mediaByType(mediaType);
                const accept = mediaType === 'IMAGE' ? 'image/png,image/jpeg,image/gif,image/webp' : 'video/mp4';
                return (
                  <div key={mediaType} className="rounded-2xl border border-slate-100 p-3">
                    {media ? <MediaPreview content={content} media={media} /> : <div className="flex aspect-video items-center justify-center rounded-xl bg-slate-50 text-xs font-semibold text-slate-400">{MEDIA_TYPE_LABELS[mediaType]} eklenmemiş</div>}
                    <div className="mt-3 flex items-center justify-between gap-2">
                      <div><p className="text-sm font-bold text-slate-700">{MEDIA_TYPE_LABELS[mediaType]}</p>{media?.modelProvider && <p className="mt-0.5 text-[10px] text-slate-400">{media.modelProvider} · {media.modelId}</p>}</div>
                      {isDraft && (
                        <div className="flex gap-2">
                          {media && <Button variant="danger" size="sm" disabled={mediaBusy === mediaType} onClick={() => removeMedia(mediaType)}>Sil</Button>}
                          <label className="inline-flex min-h-9 cursor-pointer items-center rounded-xl bg-indigo-50 px-3 text-xs font-bold text-indigo-700 transition hover:bg-indigo-100">
                            <input type="file" className="sr-only" accept={accept} onChange={(event) => uploadMedia(mediaType, event.target.files?.[0])} />
                            {mediaBusy === mediaType ? 'Yükleniyor…' : media ? 'Değiştir' : 'Yükle'}
                          </label>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="rounded-2xl border border-slate-100 p-4">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div><p className="text-sm font-bold text-slate-800">Yayın planı</p><p className="mt-1 text-xs text-slate-500">{content.scheduledAt ? formatDateTime(content.scheduledAt) : content.status === 'PUBLISHED' ? `Yayınlandı: ${formatDateTime(content.publishedAt)}` : 'Henüz planlanmadı'}</p></div>
              <div className="flex flex-wrap gap-2">
                {content.status === 'SCHEDULED' && <Button variant="danger" size="sm" disabled={busy} onClick={cancelSchedule}>Planı iptal et</Button>}
                {content.status !== 'PUBLISHED' && <Button size="sm" onClick={() => setScheduleOpen(true)}>{content.status === 'FAILED' ? 'Yeniden dene' : content.status === 'SCHEDULED' ? 'Yeniden planla' : 'Takvime ekle'}</Button>}
              </div>
            </div>
          </div>

          {isDraft && <div className="flex justify-end border-t border-slate-100 pt-5"><Button variant="danger" onClick={onDelete}>Taslağı sil</Button></div>}
        </div>
      )}

      <Modal open={scheduleOpen} title={content.status === 'FAILED' ? 'Yayınlamayı yeniden dene' : 'Yayın zamanı'} description={`${PLATFORM_LABELS[content.platform]} · ${CONTENT_TYPE_LABELS[content.contentType]}`} onClose={() => setScheduleOpen(false)} size="md">
        <ScheduleForm content={content} notify={notify} onSaved={(updated) => { setScheduleOpen(false); onChanged(updated); }} />
      </Modal>
    </>
  );
}

export default function ContentsPage({ notify }) {
  const [platforms, setPlatforms] = useState([]);
  const [data, setData] = useState(null);
  const [filters, setFilters] = useState({ status: '', platform: '', batchId: '', page: 0, size: 20 });
  const [batchInput, setBatchInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [content, setContent] = useState(null);
  const [attempts, setAttempts] = useState([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [attemptsLoading, setAttemptsLoading] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [scheduleTarget, setScheduleTarget] = useState(null);

  useEffect(() => { api.getPlatforms().then(setPlatforms).catch(() => {}); }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api.listContents(filters));
      setError(null);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => { load(); }, [load]);

  const openDetail = useCallback(async (id) => {
    setSelectedId(id);
    setContent(null);
    setDetailLoading(true);
    setAttemptsLoading(true);
    try {
      const detail = await api.getContent(id);
      setContent(detail);
    } catch (detailError) {
      notify(detailError.message, 'error');
      setSelectedId(null);
    } finally {
      setDetailLoading(false);
    }
    try {
      setAttempts(await api.getPublishAttempts(id));
    } catch (attemptError) {
      notify(attemptError.message, 'error');
      setAttempts([]);
    } finally {
      setAttemptsLoading(false);
    }
  }, [notify]);

  const setFilter = (name, value) => setFilters((current) => ({ ...current, [name]: value, page: name === 'page' ? value : 0 }));

  const applyBatchFilter = (event) => {
    event.preventDefault();
    const value = batchInput.trim();
    if (value && !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)) {
      notify('Geçerli bir batch UUID girin.', 'error');
      return;
    }
    setFilter('batchId', value);
  };

  const changed = (updated) => {
    setContent(updated);
    setData((current) => current ? { ...current, items: current.items.map((item) => item.id === updated.id ? updated : item) } : current);
  };

  const confirmDelete = async () => {
    setDeleteBusy(true);
    try {
      await api.deleteContent(deleteTarget.id);
      notify('Taslak silindi.');
      setDeleteTarget(null);
      setSelectedId(null);
      setContent(null);
      load();
    } catch (deleteError) {
      notify(deleteError.message, 'error');
    } finally {
      setDeleteBusy(false);
    }
  };

  const statusCounts = useMemo(() => {
    const counts = { DRAFT: 0, SCHEDULED: 0, PUBLISHED: 0, FAILED: 0 };
    data?.items?.forEach((item) => { counts[item.status] += 1; });
    return counts;
  }, [data]);

  return (
    <>
      <PageHeader eyebrow="Taslak yönetimi" title="İçerik kütüphanesi" description="Üretilen içerikleri düzenleyin, medyalarını yönetin ve yayın akışına alın." />

      <div className="mb-5 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {Object.entries(CONTENT_STATUS_META).filter(([key]) => key !== 'DEFAULT').map(([key, meta]) => (
          <button key={key} onClick={() => setFilter('status', filters.status === key ? '' : key)} className={`rounded-2xl border p-4 text-left transition ${filters.status === key ? 'border-indigo-300 bg-indigo-50' : 'border-slate-200 bg-white hover:border-slate-300'}`}>
            <p className="text-xs font-bold text-slate-400">{meta.label}</p><p className="mt-2 text-2xl font-extrabold text-slate-900">{statusCounts[key]}</p><p className="mt-1 text-[10px] text-slate-400">Bu sayfadaki kayıt</p>
          </button>
        ))}
      </div>

      <Card className="overflow-hidden">
        <div className="grid gap-3 border-b border-slate-100 p-5 sm:grid-cols-3">
          <Select value={filters.status} onChange={(event) => setFilter('status', event.target.value)}><option value="">Tüm durumlar</option>{Object.entries(CONTENT_STATUS_META).filter(([key]) => key !== 'DEFAULT').map(([key, meta]) => <option key={key} value={key}>{meta.label}</option>)}</Select>
          <Select value={filters.platform} onChange={(event) => setFilter('platform', event.target.value)}><option value="">Tüm platformlar</option>{platforms.map((item) => <option key={item.platform} value={item.platform}>{PLATFORM_LABELS[item.platform] || item.platform}</option>)}</Select>
          <form className="flex gap-2" onSubmit={applyBatchFilter}>
            <Input value={batchInput} onChange={(event) => setBatchInput(event.target.value)} placeholder="Batch UUID ile filtrele" />
            <Button type="submit" variant="secondary" className="shrink-0">Uygula</Button>
          </form>
        </div>

        <div className="p-5">
          {loading && <Spinner label="İçerikler yükleniyor" />}
          {!loading && error && <ErrorState error={error} onRetry={load} />}
          {!loading && !error && !data?.items?.length && <EmptyState title="İçerik bulunamadı" description="Filtreleri temizleyin veya üretim ekranından yeni bir batch başlatın." action={<Button onClick={() => { window.location.hash = '#/batches'; }}>Üretime git</Button>} />}
          {!loading && !error && data?.items?.length > 0 && (
            <div className="grid gap-4 xl:grid-cols-2">
              {data.items.map((item) => {
                const meta = CONTENT_STATUS_META[item.status] || CONTENT_STATUS_META.DEFAULT;
                const image = item.media?.find((media) => media.mediaType === 'IMAGE');
                return (
                  <article key={item.id} className="group overflow-hidden rounded-2xl border border-slate-100 bg-white transition hover:border-indigo-200 hover:shadow-md hover:shadow-slate-200/40">
                    <button className="grid w-full text-left sm:grid-cols-[150px_1fr]" onClick={() => openDetail(item.id)}>
                      <div className="min-h-36 bg-slate-50">
                        {image ? <MediaPreview content={item} media={image} /> : <div className="flex h-full min-h-36 items-center justify-center bg-gradient-to-br from-slate-100 to-indigo-50 text-2xl text-slate-300">✦</div>}
                      </div>
                      <div className="min-w-0 p-5">
                        <div className="flex flex-wrap items-center gap-2"><StatusBadge label={meta.label} tone={meta.tone} /><span className="text-[11px] font-bold text-slate-400">{PLATFORM_LABELS[item.platform]} · {CONTENT_TYPE_LABELS[item.contentType]}</span></div>
                        <p className="mt-3 text-sm font-semibold leading-6 text-slate-700">{truncate(item.text, 145)}</p>
                        <div className="mt-3 flex flex-wrap gap-1.5">{item.hashtags?.slice(0, 3).map((tag) => <span key={tag} className="text-xs font-semibold text-indigo-500">{tag}</span>)}</div>
                        <p className="mt-4 text-[11px] font-medium text-slate-400">{item.scheduledAt ? formatDateTime(item.scheduledAt) : formatDateTime(item.createdAt)}</p>
                      </div>
                    </button>
                    <div className="flex justify-end gap-2 border-t border-slate-100 px-4 py-3">
                      {item.status !== 'PUBLISHED' && <Button size="sm" variant={item.status === 'FAILED' ? 'danger' : 'soft'} onClick={() => setScheduleTarget(item)}>{item.status === 'FAILED' ? 'Yeniden dene' : item.status === 'SCHEDULED' ? 'Yeniden planla' : 'Planla'}</Button>}
                      <Button size="sm" variant="ghost" onClick={() => openDetail(item.id)}>Detay</Button>
                    </div>
                  </article>
                );
              })}
              <div className="xl:col-span-2"><Pagination page={data.page} totalPages={data.totalPages} onChange={(page) => setFilter('page', page)} /></div>
            </div>
          )}
        </div>
      </Card>

      <Modal open={Boolean(selectedId)} title="İçerik detayı" description={content ? `Oluşturulma: ${formatDateTime(content.createdAt)}` : 'İçerik yükleniyor'} onClose={() => { setSelectedId(null); setContent(null); }} size="xl">
        {detailLoading || !content ? <Spinner label="İçerik detayı yükleniyor" /> : <ContentDetail content={content} attempts={attempts} attemptsLoading={attemptsLoading} notify={notify} onChanged={changed} onDelete={() => setDeleteTarget(content)} />}
      </Modal>

      <Modal open={Boolean(scheduleTarget)} title={scheduleTarget?.status === 'FAILED' ? 'Yayınlamayı yeniden dene' : 'Yayın zamanı'} description={scheduleTarget ? `${PLATFORM_LABELS[scheduleTarget.platform]} · ${CONTENT_TYPE_LABELS[scheduleTarget.contentType]}` : ''} onClose={() => setScheduleTarget(null)} size="md">
        {scheduleTarget && <ScheduleForm content={scheduleTarget} notify={notify} onSaved={(updated) => { setScheduleTarget(null); changed(updated); load(); }} />}
      </Modal>

      <ConfirmDialog open={Boolean(deleteTarget)} title="Taslak silinsin mi?" description="Bu işlem taslağı ve yüklenmiş medyalarını kalıcı olarak siler." busy={deleteBusy} onClose={() => setDeleteTarget(null)} onConfirm={confirmDelete} />
    </>
  );
}

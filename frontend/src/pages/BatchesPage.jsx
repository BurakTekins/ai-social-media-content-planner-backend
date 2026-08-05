import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import {
  BATCH_STATUS_META,
  CONTENT_TYPE_LABELS,
  PLATFORM_LABELS,
  SOURCE_STATUS_META,
  formatDateTime,
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
  Progress,
  Select,
  Spinner,
  StatusBadge,
  Textarea,
} from '../components/ui';

const initialForm = {
  platform: '',
  contentType: '',
  requestedCount: 5,
  includeImage: false,
  includeVideo: false,
  textProvider: '',
  textModel: '',
  imageProvider: '',
  imageModel: '',
  videoProvider: '',
  videoModel: '',
  links: '',
};

function linksExist(value) {
  return value.split('\n').some((item) => item.trim());
}

function ModelPicker({ capability, models, provider, model, onChange, required }) {
  const providers = useMemo(
    () => {
      const available = [...new Set(models.map((item) => item.providerName))];
      return (available.length ? available : ['openai', 'anthropic', 'gemini', 'deepseek', 'qwen'])
        .sort((a, b) => a.localeCompare(b));
    },
    [models],
  );
  const providerModels = useMemo(
    () => models.filter((item) => item.providerName === provider),
    [models, provider],
  );

  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <Field label="Sağlayıcı" required={required}>
        <Select
          value={provider}
          required={required}
          onChange={(event) => onChange(event.target.value, '')}
        >
          <option value="">Sağlayıcı seçin</option>
          {providers.map((item) => <option key={item} value={item}>{item}</option>)}
        </Select>
      </Field>
      <Field label={`${capability} modeli`} hint={!models.length ? 'Katalog boş: model ID girin' : undefined} required={required}>
        {models.length ? (
          <Select
            value={model}
            required={required}
            disabled={!provider}
            onChange={(event) => onChange(provider, event.target.value)}
          >
            <option value="">Model seçin</option>
            {providerModels.map((item) => (
              <option key={item.id || `${item.providerName}-${item.modelId}`} value={item.modelId}>
                {item.displayName || item.modelId}
              </option>
            ))}
          </Select>
        ) : (
          <Input
            value={model}
            required={required}
            disabled={!provider}
            placeholder="Model ID"
            onChange={(event) => onChange(provider, event.target.value)}
          />
        )}
      </Field>
    </div>
  );
}

function BatchForm({ platforms, models, onCreated, notify }) {
  const [form, setForm] = useState(initialForm);
  const [files, setFiles] = useState([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (form.platform || !platforms.length) return;
    const first = platforms[0];
    setForm((current) => ({
      ...current,
      platform: first.platform,
      contentType: first.contentTypes?.[0] || '',
    }));
  }, [form.platform, platforms]);

  const supportedTypes = platforms.find((item) => item.platform === form.platform)?.contentTypes || [];
  const byCapability = (capability) => models.filter((item) => item.capability === capability);

  const setValue = (name, value) => setForm((current) => ({ ...current, [name]: value }));
  const setModel = (prefix, provider, model) => setForm((current) => ({
    ...current,
    [`${prefix}Provider`]: provider,
    [`${prefix}Model`]: model,
  }));

  const handlePlatform = (platform) => {
    const contentTypes = platforms.find((item) => item.platform === platform)?.contentTypes || [];
    setForm((current) => ({ ...current, platform, contentType: contentTypes[0] || '' }));
  };

  const submit = async (event) => {
    event.preventDefault();
    if (!linksExist(form.links) && files.length === 0) {
      notify('En az bir kaynak linki veya döküman ekleyin.', 'error');
      return;
    }
    if (!form.textProvider || !form.textModel) {
      notify('Metin sağlayıcısı ve modeli zorunlu.', 'error');
      return;
    }
    if (form.includeImage && (!form.imageProvider || !form.imageModel)) {
      notify('Görsel üretimi için sağlayıcı ve model seçin.', 'error');
      return;
    }
    if (form.includeVideo && (!form.videoProvider || !form.videoModel)) {
      notify('Video üretimi için sağlayıcı ve model seçin.', 'error');
      return;
    }

    const links = form.links.split('\n').map((item) => item.trim()).filter(Boolean);
    const payload = {
      platform: form.platform,
      contentType: form.contentType,
      requestedCount: Number(form.requestedCount),
      includeImage: form.includeImage,
      includeVideo: form.includeVideo,
      textModel: { provider: form.textProvider, model: form.textModel },
      imageModel: form.includeImage ? { provider: form.imageProvider, model: form.imageModel } : null,
      videoModel: form.includeVideo ? { provider: form.videoProvider, model: form.videoModel } : null,
      links,
    };

    setBusy(true);
    try {
      const created = await api.createBatch(payload, files);
      notify('Üretim isteği kuyruğa alındı.');
      onCreated(created);
    } catch (error) {
      notify(error.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-7">
      <div>
        <p className="eyebrow">1 · Format</p>
        <div className="mt-3 grid gap-4 sm:grid-cols-3">
          <Field label="Platform" required>
            <Select value={form.platform} required onChange={(event) => handlePlatform(event.target.value)}>
              {platforms.map((item) => (
                <option key={item.platform} value={item.platform}>{PLATFORM_LABELS[item.platform] || item.platform}</option>
              ))}
            </Select>
          </Field>
          <Field label="İçerik türü" required>
            <Select value={form.contentType} required onChange={(event) => setValue('contentType', event.target.value)}>
              {supportedTypes.map((item) => <option key={item} value={item}>{CONTENT_TYPE_LABELS[item] || item}</option>)}
            </Select>
          </Field>
          <Field label="İçerik adedi" hint="En az 1" required>
            <Input
              type="number"
              min="1"
              required
              value={form.requestedCount}
              onChange={(event) => setValue('requestedCount', event.target.value)}
            />
          </Field>
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          {[
            ['includeImage', 'Görsel üret', 'Her taslağa bir görsel eklenir.'],
            ['includeVideo', 'Video üret', 'Her taslağa bir video eklenir.'],
          ].map(([name, label, description]) => (
            <label key={name} className={`flex cursor-pointer items-start gap-3 rounded-2xl border p-4 transition ${form[name] ? 'border-indigo-200 bg-indigo-50/60' : 'border-slate-200 hover:bg-slate-50'}`}>
              <input
                type="checkbox"
                className="mt-0.5 h-4 w-4 accent-indigo-600"
                checked={form[name]}
                onChange={(event) => setValue(name, event.target.checked)}
              />
              <span>
                <span className="block text-sm font-bold text-slate-800">{label}</span>
                <span className="mt-1 block text-xs leading-5 text-slate-500">{description}</span>
              </span>
            </label>
          ))}
        </div>
      </div>

      <div className="border-t border-slate-100 pt-7">
        <p className="eyebrow">2 · Yapay zeka modelleri</p>
        {!models.length && (
          <p className="mt-3 rounded-xl border border-amber-100 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-700">
            Model kataloğu boş döndü. Mock akışı için sağlayıcıyı seçip backend'in kabul edeceği model ID'sini elle girebilirsiniz.
          </p>
        )}
        <div className="mt-4 space-y-5">
          <div className="rounded-2xl bg-slate-50 p-4">
            <p className="mb-3 text-sm font-bold text-slate-800">Metin ve hashtag</p>
            <ModelPicker capability="Metin" models={byCapability('TEXT')} provider={form.textProvider} model={form.textModel} required onChange={(provider, model) => setModel('text', provider, model)} />
          </div>
          {form.includeImage && (
            <div className="rounded-2xl bg-slate-50 p-4">
              <p className="mb-3 text-sm font-bold text-slate-800">Görsel</p>
              <ModelPicker capability="Görsel" models={byCapability('IMAGE')} provider={form.imageProvider} model={form.imageModel} required onChange={(provider, model) => setModel('image', provider, model)} />
            </div>
          )}
          {form.includeVideo && (
            <div className="rounded-2xl bg-slate-50 p-4">
              <p className="mb-3 text-sm font-bold text-slate-800">Video</p>
              <ModelPicker capability="Video" models={byCapability('VIDEO')} provider={form.videoProvider} model={form.videoModel} required onChange={(provider, model) => setModel('video', provider, model)} />
            </div>
          )}
        </div>
      </div>

      <div className="border-t border-slate-100 pt-7">
        <p className="eyebrow">3 · Kaynaklar</p>
        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <Field label="Kaynak linkleri" hint="Her satıra bir link">
            <Textarea
              value={form.links}
              onChange={(event) => setValue('links', event.target.value)}
              placeholder={'https://ornek.com/yazi\nhttps://ornek.com/urun'}
            />
          </Field>
          <Field label="Dökümanlar" hint="PDF, DOCX veya TXT">
            <label className="flex min-h-28 cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 text-center transition hover:border-indigo-300 hover:bg-indigo-50/40">
              <input
                className="sr-only"
                type="file"
                multiple
                accept=".pdf,.docx,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
                onChange={(event) => setFiles([...event.target.files])}
              />
              <span className="text-sm font-bold text-slate-700">Dosya seçin</span>
              <span className="mt-1 text-xs text-slate-400">Birden fazla dosya yükleyebilirsiniz</span>
              {files.length > 0 && <span className="mt-3 rounded-full bg-indigo-100 px-3 py-1 text-xs font-bold text-indigo-700">{files.length} dosya seçildi</span>}
            </label>
          </Field>
        </div>
      </div>

      <div className="flex flex-col-reverse gap-3 border-t border-slate-100 pt-6 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-xs leading-5 text-slate-400">İstek arka planda işlenecek; ilerlemeyi listeden izleyebilirsiniz.</p>
        <Button type="submit" size="lg" disabled={busy || !platforms.length}>
          {busy ? 'Kuyruğa alınıyor…' : 'Üretimi başlat'}
        </Button>
      </div>
    </form>
  );
}

function BatchDetail({ batch, loading, retryBusy, onRetry, onClose }) {
  if (loading || !batch) return <Spinner label="Batch detayı yükleniyor" />;
  const meta = BATCH_STATUS_META[batch.status] || BATCH_STATUS_META.DEFAULT;
  const progress = batch.requestedCount ? (batch.completedCount / batch.requestedCount) * 100 : 0;

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="rounded-2xl bg-slate-50 p-4"><p className="text-xs font-semibold text-slate-400">Platform</p><p className="mt-1 font-bold text-slate-800">{PLATFORM_LABELS[batch.platform] || batch.platform}</p></div>
        <div className="rounded-2xl bg-slate-50 p-4"><p className="text-xs font-semibold text-slate-400">Format</p><p className="mt-1 font-bold text-slate-800">{CONTENT_TYPE_LABELS[batch.contentType] || batch.contentType}</p></div>
        <div className="rounded-2xl bg-slate-50 p-4"><p className="text-xs font-semibold text-slate-400">Durum</p><div className="mt-1"><StatusBadge label={meta.label} tone={meta.tone} /></div></div>
      </div>
      <Progress value={progress} label={`${batch.completedCount} / ${batch.requestedCount} içerik hazır`} />
      {batch.status === 'FAILED' && batch.lastError && (
        <div className="rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          <p className="font-bold">Son hata</p>
          <p className="mt-1 break-words leading-6">{batch.lastError}</p>
        </div>
      )}
      <div>
        <h3 className="text-sm font-bold text-slate-800">Model seçimi</h3>
        <div className="mt-3 divide-y divide-slate-100 rounded-2xl border border-slate-100">
          {[
            ['Metin', batch.textProvider, batch.textModel],
            ['Görsel', batch.imageProvider, batch.imageModel],
            ['Video', batch.videoProvider, batch.videoModel],
          ].filter(([, provider]) => provider).map(([label, provider, model]) => (
            <div key={label} className="flex items-center justify-between gap-4 px-4 py-3 text-sm">
              <span className="font-semibold text-slate-500">{label}</span>
              <span className="text-right font-bold text-slate-800">{provider} · {model}</span>
            </div>
          ))}
        </div>
      </div>
      <div>
        <h3 className="text-sm font-bold text-slate-800">Kaynaklar</h3>
        <div className="mt-3 space-y-2">
          {!batch.sources?.length && <p className="rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-500">Kaynak eklenmemiş.</p>}
          {batch.sources?.map((source) => {
            const sourceMeta = SOURCE_STATUS_META[source.status] || SOURCE_STATUS_META.DEFAULT;
            return (
              <div key={source.id} className="flex flex-col gap-2 rounded-xl border border-slate-100 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <p className="text-xs font-bold uppercase tracking-wide text-slate-400">{source.sourceType}</p>
                  <p className="mt-1 truncate text-sm font-medium text-slate-700">{source.sourceValue}</p>
                  {source.errorMessage && <p className="mt-1 text-xs text-rose-600">{source.errorMessage}</p>}
                </div>
                <StatusBadge label={sourceMeta.label} tone={sourceMeta.tone} />
              </div>
            );
          })}
        </div>
      </div>
      <div className="flex justify-end gap-3">
        {batch.status === 'FAILED' && (
          <Button onClick={onRetry} disabled={retryBusy}>
            {retryBusy ? 'Başlatılıyor…' : 'Tekrar dene'}
          </Button>
        )}
        <Button variant="secondary" onClick={onClose} disabled={retryBusy}>Kapat</Button>
      </div>
    </div>
  );
}

export default function BatchesPage({ notify }) {
  const [createOpen, setCreateOpen] = useState(false);
  const [platforms, setPlatforms] = useState([]);
  const [models, setModels] = useState([]);
  const [metadataLoading, setMetadataLoading] = useState(true);
  const [metadataError, setMetadataError] = useState(null);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [filters, setFilters] = useState({ status: '', platform: '', contentType: '', page: 0, size: 20 });
  const [selectedId, setSelectedId] = useState(null);
  const [selectedBatch, setSelectedBatch] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [retryConfirmOpen, setRetryConfirmOpen] = useState(false);
  const [retryBusy, setRetryBusy] = useState(false);

  const loadMetadata = useCallback(async () => {
    setMetadataLoading(true);
    try {
      const [platformItems, text, image, video] = await Promise.all([
        api.getPlatforms(),
        api.getAiModels({ capability: 'TEXT' }),
        api.getAiModels({ capability: 'IMAGE' }),
        api.getAiModels({ capability: 'VIDEO' }),
      ]);
      setPlatforms(platformItems);
      setModels([...text, ...image, ...video]);
      setMetadataError(null);
    } catch (metadataLoadError) {
      setMetadataError(metadataLoadError);
    } finally {
      setMetadataLoading(false);
    }
  }, []);

  const loadBatches = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      setData(await api.listBatches(filters));
      setError(null);
    } catch (loadError) {
      setError(loadError);
    } finally {
      if (!quiet) setLoading(false);
    }
  }, [filters]);

  useEffect(() => { loadMetadata(); }, [loadMetadata]);
  useEffect(() => { loadBatches(); }, [loadBatches]);

  useEffect(() => {
    const hasActive = data?.items?.some((item) => item.status === 'IN_PROGRESS');
    if (!hasActive) return undefined;
    const interval = window.setInterval(() => loadBatches(true), 3000);
    return () => window.clearInterval(interval);
  }, [data?.items, loadBatches]);

  useEffect(() => {
    if (!selectedId) return undefined;
    let cancelled = false;
    const loadDetail = async () => {
      setDetailLoading(true);
      try {
        const detail = await api.getBatch(selectedId);
        if (!cancelled) setSelectedBatch(detail);
      } catch (detailError) {
        if (!cancelled) notify(detailError.message, 'error');
      } finally {
        if (!cancelled) setDetailLoading(false);
      }
    };
    loadDetail();
    return () => { cancelled = true; };
  }, [selectedId, notify]);

  useEffect(() => {
    if (!selectedId || selectedBatch?.status !== 'IN_PROGRESS') return undefined;
    const interval = window.setInterval(async () => {
      try { setSelectedBatch(await api.getBatch(selectedId)); } catch { }
    }, 3000);
    return () => window.clearInterval(interval);
  }, [selectedId, selectedBatch?.status]);

  const setFilter = (name, value) => setFilters((current) => ({ ...current, [name]: value, page: name === 'page' ? value : 0 }));
  const contentTypes = filters.platform
    ? platforms.find((item) => item.platform === filters.platform)?.contentTypes || []
    : [...new Set(platforms.flatMap((item) => item.contentTypes || []))];

  const handleCreated = (created) => {
    setCreateOpen(false);
    setSelectedId(created.id);
    loadBatches();
  };

  const closeDetail = () => {
    if (retryBusy) return;
    setRetryConfirmOpen(false);
    setSelectedId(null);
    setSelectedBatch(null);
  };

  const handleRetry = async () => {
    if (!selectedBatch || retryBusy) return;

    const batchId = selectedBatch.id;
    setRetryBusy(true);
    try {
      const retried = await api.retryBatch(batchId);
      if (retried && typeof retried === 'object') setSelectedBatch(retried);
      setRetryConfirmOpen(false);
      notify('Üretim tekrar başlatıldı.');

      try {
        const detail = await api.getBatch(batchId);
        setSelectedBatch(detail);
      } catch (refreshError) {
        notify(`Üretim başladı ancak detay yenilenemedi: ${refreshError.message}`, 'error');
      }
      await loadBatches(true);
    } catch (retryError) {
      notify(retryError.message, 'error');
    } finally {
      setRetryBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        eyebrow="Toplu üretim"
        title="İçerik üretim merkezi"
        description="Kaynaklarınızı, platform formatını ve yapay zeka modellerini seçin; batch ilerlemesini tek ekrandan izleyin."
        action={<Button size="lg" onClick={() => setCreateOpen(true)}>＋ Yeni üretim</Button>}
      />

      {metadataError && <div className="mb-5"><ErrorState error={metadataError} onRetry={loadMetadata} /></div>}

      <Card className="overflow-hidden">
        <div className="flex flex-col gap-4 border-b border-slate-100 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div><h2 className="font-bold text-slate-900">Üretim geçmişi</h2><p className="mt-1 text-xs text-slate-400">Devam eden batch'ler otomatik yenilenir.</p></div>
          <div className="grid gap-2 sm:grid-cols-3">
            <Select value={filters.status} onChange={(event) => setFilter('status', event.target.value)} className="min-w-36">
              <option value="">Tüm durumlar</option>
              {Object.entries(BATCH_STATUS_META).filter(([key]) => key !== 'DEFAULT').map(([key, meta]) => <option key={key} value={key}>{meta.label}</option>)}
            </Select>
            <Select value={filters.platform} onChange={(event) => setFilters((current) => ({ ...current, platform: event.target.value, contentType: '', page: 0 }))} className="min-w-36">
              <option value="">Tüm platformlar</option>
              {platforms.map((item) => <option key={item.platform} value={item.platform}>{PLATFORM_LABELS[item.platform] || item.platform}</option>)}
            </Select>
            <Select value={filters.contentType} onChange={(event) => setFilter('contentType', event.target.value)} className="min-w-36">
              <option value="">Tüm formatlar</option>
              {contentTypes.map((item) => <option key={item} value={item}>{CONTENT_TYPE_LABELS[item] || item}</option>)}
            </Select>
          </div>
        </div>

        <div className="p-5">
          {loading && <Spinner label="Üretimler yükleniyor" />}
          {!loading && error && <ErrorState error={error} onRetry={() => loadBatches()} />}
          {!loading && !error && !data?.items?.length && (
            <EmptyState title="Henüz üretim yok" description="İlk toplu içerik üretiminizi başlatarak batch ilerlemesini burada izleyin." action={<Button onClick={() => setCreateOpen(true)}>Yeni üretim</Button>} />
          )}
          {!loading && !error && data?.items?.length > 0 && (
            <div className="space-y-3">
              {data.items.map((batch) => {
                const meta = BATCH_STATUS_META[batch.status] || BATCH_STATUS_META.DEFAULT;
                const progress = batch.requestedCount ? (batch.completedCount / batch.requestedCount) * 100 : 0;
                return (
                  <button
                    key={batch.id}
                    className="grid w-full gap-4 rounded-2xl border border-slate-100 p-4 text-left transition hover:border-indigo-200 hover:shadow-sm lg:grid-cols-[1.2fr_1fr_1fr_160px] lg:items-center"
                    onClick={() => setSelectedId(batch.id)}
                  >
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2"><StatusBadge label={meta.label} tone={meta.tone} /><span className="text-xs font-semibold text-slate-400">{formatDateTime(batch.createdAt)}</span></div>
                      <p className="mt-2 truncate font-bold text-slate-900">{PLATFORM_LABELS[batch.platform] || batch.platform} · {CONTENT_TYPE_LABELS[batch.contentType] || batch.contentType}</p>
                      <p className="mt-1 truncate text-xs text-slate-400">{batch.textProvider} · {batch.textModel}</p>
                    </div>
                    <div className="flex gap-2 text-xs font-semibold text-slate-500">
                      {batch.includeImage && <span className="rounded-lg bg-slate-100 px-2.5 py-1.5">Görsel</span>}
                      {batch.includeVideo && <span className="rounded-lg bg-slate-100 px-2.5 py-1.5">Video</span>}
                      {!batch.includeImage && !batch.includeVideo && <span>Yalnızca metin</span>}
                    </div>
                    <Progress value={progress} label={`${batch.completedCount} / ${batch.requestedCount} hazır`} />
                    <span className="text-right text-xs font-bold text-indigo-600">Detayı görüntüle →</span>
                  </button>
                );
              })}
              <Pagination page={data.page} totalPages={data.totalPages} onChange={(page) => setFilter('page', page)} />
            </div>
          )}
        </div>
      </Card>

      <Modal open={createOpen} title="Yeni toplu üretim" description="Üretim ayarlarını ve ortak kaynakları belirleyin." onClose={() => setCreateOpen(false)} size="xl">
        {metadataLoading ? (
          <Spinner label="Platformlar ve modeller hazırlanıyor" />
        ) : !platforms.length ? (
          <ErrorState error={metadataError || new Error('Platform metadata listesi boş döndü.')} onRetry={loadMetadata} />
        ) : (
          <BatchForm platforms={platforms} models={models} onCreated={handleCreated} notify={notify} />
        )}
      </Modal>

      <Modal open={Boolean(selectedId)} title="Üretim detayı" description={selectedBatch ? `Batch ${selectedBatch.id}` : 'Batch bilgileri'} onClose={closeDetail} size="lg">
        <BatchDetail
          batch={selectedBatch}
          loading={detailLoading}
          retryBusy={retryBusy}
          onRetry={() => setRetryConfirmOpen(true)}
          onClose={closeDetail}
        />
      </Modal>

      <ConfirmDialog
        open={retryConfirmOpen}
        title="Üretimi tekrar dene"
        description="Yalnızca eksik içerikler üretilecek; tamamlanan içerikler, alınmış AI çıktıları ve hazırlanmış kaynaklar korunacak. Provider yanıtı uygulamaya hiç ulaşmadıysa yeni çağrı tekrar ücret doğurabilir. Devam edilsin mi?"
        confirmLabel="Tekrar dene"
        busy={retryBusy}
        onConfirm={handleRetry}
        onClose={() => { if (!retryBusy) setRetryConfirmOpen(false); }}
      />
    </>
  );
}

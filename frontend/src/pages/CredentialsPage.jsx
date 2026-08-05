import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import {
  CREDENTIAL_TYPE_LABELS,
  formatDateTime,
  toDateTimeLocal,
  toIsoFromLocal,
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
  Spinner,
  StatusBadge,
} from '../components/ui';

const providers = {
  AI_PROVIDER: [
    ['openai', 'OpenAI'],
    ['anthropic', 'Anthropic / Claude'],
    ['gemini', 'Google Gemini'],
    ['deepseek', 'DeepSeek'],
    ['qwen', 'Qwen'],
  ],
  SOCIAL_PLATFORM: [
    ['linkedin', 'LinkedIn'],
    ['instagram', 'Instagram'],
    ['twitter', 'X / Twitter'],
  ],
};

const providerLabels = Object.values(providers).flat().reduce((labels, [key, label]) => ({ ...labels, [key]: label }), {});

function CredentialForm({ notify, onSaved }) {
  const [form, setForm] = useState({ credentialType: 'AI_PROVIDER', providerName: 'openai', accountIdentifier: '', accessToken: '', refreshToken: '', expiresAt: '' });
  const [busy, setBusy] = useState(false);
  const accountRequired = ['linkedin', 'instagram'].includes(form.providerName);
  const setValue = (name, value) => setForm((current) => ({ ...current, [name]: value }));

  const changeType = (credentialType) => setForm((current) => ({ ...current, credentialType, providerName: providers[credentialType][0][0], accountIdentifier: '' }));

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      const created = await api.createCredential({
        credentialType: form.credentialType,
        providerName: form.providerName,
        accountIdentifier: form.accountIdentifier.trim() || null,
        accessToken: form.accessToken,
        refreshToken: form.refreshToken.trim() || null,
        expiresAt: form.expiresAt ? toIsoFromLocal(form.expiresAt) : null,
      });
      notify('Credential güvenli biçimde kaydedildi.');
      onSaved(created);
    } catch (error) {
      notify(error.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Credential türü" required>
          <select className="control" value={form.credentialType} onChange={(event) => changeType(event.target.value)}>
            <option value="AI_PROVIDER">Yapay zeka sağlayıcısı</option>
            <option value="SOCIAL_PLATFORM">Sosyal medya platformu</option>
          </select>
        </Field>
        <Field label="Sağlayıcı" required>
          <select className="control" value={form.providerName} onChange={(event) => setValue('providerName', event.target.value)}>
            {providers[form.credentialType].map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select>
        </Field>
      </div>
      <Field label="Hesap tanımlayıcısı" hint={accountRequired ? 'Bu sağlayıcı için zorunlu' : 'Opsiyonel'} required={accountRequired}>
        <Input value={form.accountIdentifier} required={accountRequired} onChange={(event) => setValue('accountIdentifier', event.target.value)} placeholder={form.providerName === 'instagram' ? 'Instagram user ID' : form.providerName === 'linkedin' ? 'LinkedIn owner URN' : 'Hesap veya tenant tanımı'} />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Access token / API key" hint="Kaydettikten sonra gösterilmez" required>
          <Input type="password" autoComplete="new-password" required value={form.accessToken} onChange={(event) => setValue('accessToken', event.target.value)} />
        </Field>
        <Field label="Refresh token" hint="Opsiyonel">
          <Input type="password" autoComplete="new-password" value={form.refreshToken} onChange={(event) => setValue('refreshToken', event.target.value)} />
        </Field>
      </div>
      <Field label="Geçerlilik sonu" hint="Opsiyonel"><Input type="datetime-local" value={form.expiresAt} onChange={(event) => setValue('expiresAt', event.target.value)} /></Field>
      <div className="rounded-2xl border border-indigo-100 bg-indigo-50/60 p-4 text-xs leading-5 text-indigo-700">
        Mock mod gerçek sağlayıcılara çağrı yapmaz; yine de backend akışı seçilen sağlayıcı için aktif bir credential kaydı bekler. Test amaçlı değerleri yalnız mock mod açıkken kullanın.
      </div>
      <div className="flex justify-end"><Button type="submit" disabled={busy}>{busy ? 'Kaydediliyor…' : 'Credential ekle'}</Button></div>
    </form>
  );
}

function RotateForm({ credential, notify, onSaved }) {
  const [accessToken, setAccessToken] = useState('');
  const [refreshToken, setRefreshToken] = useState('');
  const [expiresAt, setExpiresAt] = useState(toDateTimeLocal(credential.expiresAt));
  const [busy, setBusy] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      const updated = await api.rotateCredentialTokens(credential.id, {
        accessToken,
        refreshToken: refreshToken.trim() || null,
        expiresAt: expiresAt ? toIsoFromLocal(expiresAt) : null,
      });
      notify('Tokenlar yenilendi. Değerler arayüzde saklanmadı.');
      onSaved(updated);
    } catch (error) {
      notify(error.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      <Field label="Yeni access token / API key" required><Input type="password" autoComplete="new-password" required value={accessToken} onChange={(event) => setAccessToken(event.target.value)} /></Field>
      <Field label="Yeni refresh token" hint="Boş bırakılırsa kaldırılır"><Input type="password" autoComplete="new-password" value={refreshToken} onChange={(event) => setRefreshToken(event.target.value)} /></Field>
      <Field label="Yeni geçerlilik sonu" hint="Opsiyonel"><Input type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} /></Field>
      <p className="rounded-xl bg-slate-50 p-3 text-xs leading-5 text-slate-500">Mevcut token backend tarafından response içinde dönmediği için burada görüntülenemez veya korunamaz.</p>
      <div className="flex justify-end"><Button type="submit" disabled={busy}>{busy ? 'Güncelleniyor…' : 'Tokenları yenile'}</Button></div>
    </form>
  );
}

function AccountForm({ credential, notify, onSaved }) {
  const [value, setValue] = useState(credential.accountIdentifier || '');
  const [busy, setBusy] = useState(false);
  const required = ['linkedin', 'instagram'].includes(credential.providerName);
  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      const updated = await api.updateCredentialAccount(credential.id, value.trim() || null);
      notify('Hesap tanımlayıcısı güncellendi.');
      onSaved(updated);
    } catch (error) {
      notify(error.message, 'error');
    } finally {
      setBusy(false);
    }
  };
  return <form onSubmit={submit}><Field label="Hesap tanımlayıcısı" required={required}><Input required={required} value={value} onChange={(event) => setValue(event.target.value)} /></Field><div className="mt-6 flex justify-end"><Button type="submit" disabled={busy}>{busy ? 'Kaydediliyor…' : 'Kaydet'}</Button></div></form>;
}

export default function CredentialsPage({ notify }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [rotateTarget, setRotateTarget] = useState(null);
  const [accountTarget, setAccountTarget] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [actionId, setActionId] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await api.listCredentials());
      setError(null);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { load(); }, [load]);

  const replace = (updated) => setItems((current) => current.map((item) => item.id === updated.id ? updated : item));
  const grouped = useMemo(() => ({
    AI_PROVIDER: items.filter((item) => item.credentialType === 'AI_PROVIDER'),
    SOCIAL_PLATFORM: items.filter((item) => item.credentialType === 'SOCIAL_PLATFORM'),
  }), [items]);

  const toggle = async (credential) => {
    setActionId(credential.id);
    try {
      const updated = await api.setCredentialActive(credential.id, !credential.active);
      replace(updated);
      notify(updated.active ? 'Credential etkinleştirildi.' : 'Credential pasifleştirildi.');
    } catch (toggleError) {
      notify(toggleError.message, 'error');
    } finally {
      setActionId(null);
    }
  };

  const remove = async () => {
    setDeleteBusy(true);
    try {
      await api.deleteCredential(deleteTarget.id);
      setItems((current) => current.filter((item) => item.id !== deleteTarget.id));
      setDeleteTarget(null);
      notify('Credential silindi.');
    } catch (deleteError) {
      notify(deleteError.message, 'error');
    } finally {
      setDeleteBusy(false);
    }
  };

  return (
    <>
      <PageHeader eyebrow="Güvenli bağlantılar" title="Credential yönetimi" description="Yapay zeka sağlayıcıları ve sosyal platform hesapları için şifreli erişim bilgilerini yönetin." action={<Button size="lg" onClick={() => setCreateOpen(true)}>＋ Credential ekle</Button>} />

      <div className="mb-5 rounded-2xl border border-amber-100 bg-amber-50/70 p-4 text-sm leading-6 text-amber-800">
        <strong>Token güvenliği:</strong> Access ve refresh token değerleri backend response'larında dönmez. Bu ekran yalnız refresh token varlığını ve credential durumunu gösterir.
      </div>

      {loading && <Card><Spinner label="Credential kayıtları yükleniyor" /></Card>}
      {!loading && error && <ErrorState error={error} onRetry={load} />}
      {!loading && !error && !items.length && <Card className="p-5"><EmptyState title="Credential kaydı yok" description="Mock üretim ve yayın akışlarının çalışması için seçilen sağlayıcılara placeholder credential ekleyebilirsiniz." action={<Button onClick={() => setCreateOpen(true)}>İlk credential'ı ekle</Button>} /></Card>}

      {!loading && !error && items.length > 0 && (
        <div className="grid gap-5 xl:grid-cols-2">
          {Object.entries(grouped).map(([type, credentials]) => (
            <Card key={type} className="overflow-hidden">
              <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4"><div><p className="eyebrow">{type === 'AI_PROVIDER' ? 'Üretim' : 'Yayınlama'}</p><h2 className="mt-1 font-bold text-slate-900">{CREDENTIAL_TYPE_LABELS[type]}</h2></div><span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-extrabold text-slate-600">{credentials.length}</span></div>
              <div className="divide-y divide-slate-100">
                {!credentials.length && <div className="p-5 text-sm text-slate-400">Bu grupta kayıt yok.</div>}
                {credentials.map((credential) => (
                  <article key={credential.id} className="p-5">
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex min-w-0 items-center gap-3"><span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-slate-100 text-base font-extrabold uppercase text-slate-600">{credential.providerName.slice(0, 2)}</span><div className="min-w-0"><h3 className="truncate font-bold text-slate-900">{providerLabels[credential.providerName] || credential.providerName}</h3><p className="mt-1 truncate text-xs text-slate-400">{credential.accountIdentifier || 'Hesap tanımlayıcısı yok'}</p></div></div>
                      <StatusBadge label={credential.expired ? 'Süresi doldu' : credential.active ? 'Aktif' : 'Pasif'} tone={credential.expired ? 'rose' : credential.active ? 'emerald' : 'slate'} />
                    </div>
                    <div className="mt-4 grid grid-cols-2 gap-2 rounded-xl bg-slate-50 p-3 text-xs">
                      <div><p className="font-semibold text-slate-400">Refresh token</p><p className="mt-1 font-bold text-slate-700">{credential.hasRefreshToken ? 'Kayıtlı' : 'Yok'}</p></div>
                      <div><p className="font-semibold text-slate-400">Geçerlilik</p><p className="mt-1 font-bold text-slate-700">{credential.expiresAt ? formatDateTime(credential.expiresAt) : 'Süresiz'}</p></div>
                    </div>
                    <div className="mt-4 flex flex-wrap justify-end gap-2">
                      <Button variant="ghost" size="sm" onClick={() => setAccountTarget(credential)}>Hesap</Button>
                      <Button variant="soft" size="sm" onClick={() => setRotateTarget(credential)}>Token yenile</Button>
                      <Button variant="secondary" size="sm" disabled={actionId === credential.id} onClick={() => toggle(credential)}>{credential.active ? 'Pasifleştir' : 'Etkinleştir'}</Button>
                      <Button variant="danger" size="sm" onClick={() => setDeleteTarget(credential)}>Sil</Button>
                    </div>
                  </article>
                ))}
              </div>
            </Card>
          ))}
        </div>
      )}

      <Modal open={createOpen} title="Yeni credential" description="Token değerleri kaydedildikten sonra tekrar gösterilmez." onClose={() => setCreateOpen(false)} size="lg"><CredentialForm notify={notify} onSaved={(created) => { setItems((current) => [...current, created]); setCreateOpen(false); }} /></Modal>
      <Modal open={Boolean(rotateTarget)} title="Tokenları yenile" description={rotateTarget ? providerLabels[rotateTarget.providerName] : ''} onClose={() => setRotateTarget(null)} size="md">{rotateTarget && <RotateForm credential={rotateTarget} notify={notify} onSaved={(updated) => { replace(updated); setRotateTarget(null); }} />}</Modal>
      <Modal open={Boolean(accountTarget)} title="Hesap tanımlayıcısı" description={accountTarget ? providerLabels[accountTarget.providerName] : ''} onClose={() => setAccountTarget(null)} size="md">{accountTarget && <AccountForm credential={accountTarget} notify={notify} onSaved={(updated) => { replace(updated); setAccountTarget(null); }} />}</Modal>
      <ConfirmDialog open={Boolean(deleteTarget)} title="Credential silinsin mi?" description="Bu sağlayıcı için üretim veya yayınlama, yeni bir credential eklenene kadar çalışmaz." busy={deleteBusy} onClose={() => setDeleteTarget(null)} onConfirm={remove} />
    </>
  );
}

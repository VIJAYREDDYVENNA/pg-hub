import { useRef, useState } from 'react';
import { EmptyState } from '../common/States';
import { residentApi } from '../../services/endpoints';
import { errorMessage, openFile } from '../../services/apiClient';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { dateTime, label } from '../../utils/format';
import Icon from '../common/Icon';

const CATEGORIES = [
  { value: 'ID_PROOF', label: 'ID proof' },
  { value: 'ADDRESS_PROOF', label: 'Address proof' },
  { value: 'AGREEMENT', label: 'Rental agreement' },
  { value: 'POLICE_VERIFICATION', label: 'Police verification' },
  { value: 'PHOTO', label: 'Photo' },
  { value: 'DOCUMENT', label: 'Other document' },
];
const size = (b) => (b > 1048576 ? `${(b / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`);
const icon = (t = '') => (t.includes('pdf') ? 'bi-file-earmark-pdf' : t.startsWith('image/') ? 'bi-file-earmark-image' : 'bi-file-earmark');

/** Uploaded documents of a resident. */
export default function DocumentsTab({ resident, onChanged }) {
  const { can } = useAuth();
  const { notify } = useToast();
  const [category, setCategory] = useState('ID_PROOF');
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const inputRef = useRef(null);
  const docs = resident.documents || [];

  const upload = async (e) => {
    e.preventDefault();
    if (!file) return;
    setBusy(true);
    try {
      await residentApi.uploadDocument(resident.id, file, category);
      notify('Document uploaded.');
      setFile(null);
      if (inputRef.current) inputRef.current.value = '';
      onChanged();
    } catch (ex) {
      notify(errorMessage(ex), 'danger');
    } finally { setBusy(false); }
  };

  const open = async (d) => {
    try { await openFile(d.url.replace(/^\/api/, '')); } catch (ex) { notify(errorMessage(ex, 'Could not open the file.'), 'danger'); }
  };

  return (
    <section className="hh-card mb-3">
      <h2 className="hh-card-title mb-3"><Icon className="bi bi-folder2-open me-2 text-primary-hh" />Documents</h2>
      {can('RESIDENT_MANAGE') && (
        <form className="row g-2 align-items-end mb-3 no-print" onSubmit={upload}>
          <div className="col-12 col-md-4">
            <label className="form-label" htmlFor="doc-cat">Category</label>
            <select id="doc-cat" className="form-select" value={category} onChange={(e) => setCategory(e.target.value)}>
              {CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
            </select>
          </div>
          <div className="col-12 col-md-5">
            <label className="form-label" htmlFor="doc-file">File (PDF or image)</label>
            <input id="doc-file" ref={inputRef} type="file" className="form-control" accept="application/pdf,image/*" onChange={(e) => setFile(e.target.files?.[0] || null)} />
          </div>
          <div className="col-12 col-md-3">
            <button type="submit" className="btn btn-primary w-100" disabled={!file || busy}>
              {busy ? <span className="spinner-border spinner-border-sm me-2" /> : <Icon className="bi bi-upload me-1" />}Upload
            </button>
          </div>
        </form>
      )}
      {docs.length === 0 ? <EmptyState icon="bi-folder2" title="No documents uploaded" message="Upload ID proof, agreements and other papers here." /> : (
        <div>
          {docs.map((d) => (
            <div className="hh-feed-item" key={d.id}>
              <div className="hh-feed-icon bg-primary-light text-primary-hh"><Icon className={`bi ${icon(d.contentType)}`} aria-hidden="true" /></div>
              <div className="min-w-0 flex-grow-1">
                <div className="fw-semibold text-truncate">{d.originalName}</div>
                <div className="small text-muted-hh">{CATEGORIES.find((c) => c.value === d.category)?.label || label(d.category)} · {size(d.sizeBytes)} · {dateTime(d.createdAt)}</div>
              </div>
              <button type="button" className="btn btn-sm btn-light no-print" onClick={() => open(d)}><Icon className="bi bi-box-arrow-up-right me-1" />Open</button>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

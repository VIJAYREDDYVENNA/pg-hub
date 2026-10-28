import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import useApi from '../../hooks/useApi';
import { paymentMethodApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { useHostels } from '../../context/HostelContext';
import { AsyncContent } from '../common/States';
import FormField, { applyServerErrors } from '../common/FormField';
import AuthImage from '../common/AuthImage';
import Icon from '../common/Icon';

const UPI_RE = /^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9]{1,31}$/;
const IFSC_RE = /^[A-Z]{4}0[A-Z0-9]{6}$/;
const ACCOUNT_RE = /^[0-9]{9,18}$/;

const toForm = (d) => ({
  acceptsCash: d.acceptsCash,
  acceptsUpi: d.acceptsUpi,
  upiId: d.upiId || '',
  upiPayeeName: d.upiPayeeName || '',
  acceptsBankTransfer: d.acceptsBankTransfer,
  bankAccountHolder: d.bankAccountHolder || '',
  bankName: d.bankName || '',
  bankBranch: d.bankBranch || '',
  bankIfsc: d.bankIfsc || '',
  bankAccountNumber: '',
  acceptsOnline: d.acceptsOnline,
  instructions: d.instructions || '',
});

/** Owner setup for the ways residents may pay. Bank account numbers are write-only: the server only returns the last 4 digits. */
export default function PaymentMethodsCard() {
  const { can } = useAuth();
  const { notify } = useToast();
  const { hostels } = useHostels();
  const editable = can('SETTINGS_MANAGE');
  const [scope, setScope] = useState('');
  const hostelId = scope ? Number(scope) : undefined;
  const { data, setData, loading, error, reload } = useApi(() => paymentMethodApi.get(hostelId), [hostelId]);
  const [serverError, setServerError] = useState(null);
  const [removeAccount, setRemoveAccount] = useState(false);
  const fileRef = useRef(null);
  const { register, handleSubmit, reset, setError, watch, formState: { errors, isSubmitting, isDirty } } = useForm({ defaultValues: { acceptsCash: true } });
  useEffect(() => { if (data) { reset(toForm(data)); setRemoveAccount(false); } }, [data, reset]);

  const upi = watch('acceptsUpi');
  const bank = watch('acceptsBankTransfer');

  const onSubmit = async (v) => {
    setServerError(null);
    const body = {
      ...v,
      upiId: v.upiId.trim(),
      bankIfsc: v.bankIfsc.trim().toUpperCase(),
      // null keeps the stored number, '' removes it, digits replace it
      bankAccountNumber: removeAccount ? '' : (v.bankAccountNumber.trim() || null),
    };
    try {
      setData(await paymentMethodApi.update(hostelId, body));
      notify('Payment methods saved');
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not save the payment methods.'));
    }
  };

  const uploadQr = async (file) => {
    if (!file) return;
    try { setData(await paymentMethodApi.uploadQr(hostelId, file)); notify('QR code saved'); }
    catch (e) { notify(errorMessage(e), 'danger'); }
    if (fileRef.current) fileRef.current.value = '';
  };
  const removeQr = async () => {
    try { setData(await paymentMethodApi.removeQr(hostelId)); notify('QR code removed'); }
    catch (e) { notify(errorMessage(e), 'danger'); }
  };
  const useDefault = async () => {
    try { setData(await paymentMethodApi.removeOverride(hostelId)); notify('This hostel now follows the default'); }
    catch (e) { notify(errorMessage(e), 'danger'); }
  };

  const check = (name, text, help) => (
    <div className="form-check mb-2">
      <input id={`pm-${name}`} type="checkbox" className="form-check-input" {...register(name)} />
      <label htmlFor={`pm-${name}`} className="form-check-label">{text}</label>
      {help && <div className="form-text mt-0">{help}</div>}
    </div>
  );

  return (
    <section className="hh-card h-100" aria-labelledby="pm-title">
      <div className="hh-card-header">
        <h2 className="hh-card-title" id="pm-title"><Icon className="bi bi-wallet2 me-2" />Payment methods</h2>
        {!editable && <span className="badge hh-badge hh-badge-secondary"><Icon className="bi bi-lock me-1" />Read-only</span>}
      </div>
      <div className="alert alert-info py-2 small" role="note">
        {data?.note || 'UPI and bank-transfer payments made outside the online payment button are not detected automatically.'}
      </div>
      {hostels.length > 1 && (
        <div className="mb-3">
          <label htmlFor="pm-scope" className="form-label">Applies to</label>
          <select id="pm-scope" className="form-select" value={scope} onChange={(e) => setScope(e.target.value)}>
            <option value="">All hostels (default)</option>
            {hostels.map((h) => <option key={h.id} value={h.id}>{h.name} only</option>)}
          </select>
          {data && hostelId && (
            <div className="form-text">
              {data.inherited ? 'This hostel follows the default. Saving creates its own settings.' : 'This hostel has its own settings.'}
              {!data.inherited && editable && <> <button type="button" className="btn btn-link btn-sm p-0 align-baseline" onClick={useDefault}>Use the default instead</button></>}
            </div>
          )}
        </div>
      )}
      <AsyncContent loading={loading && !data} error={error} onRetry={reload} compact>
        {data && !data.encryptionAvailable && (
          <div className="alert alert-warning py-2 small">Bank account numbers cannot be saved on this server yet because no data-encryption key is configured.</div>
        )}
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          {serverError && <div className="alert alert-danger py-2">{serverError}</div>}
          <fieldset disabled={!editable}>
            {check('acceptsOnline', 'Online payment (card, UPI, netbanking through the payment gateway)', 'Confirmed automatically. Turn off to stop pay links and online checkout.')}
            {check('acceptsCash', 'Cash')}
            {check('acceptsUpi', 'UPI (manual)', 'Residents pay to your UPI ID or QR. You mark the payment received yourself.')}
            {upi && (
              <div className="ps-4 mb-2">
                <FormField label="UPI ID" name="upiId" register={register} errors={errors}
                  rules={{ required: 'Enter your UPI ID', validate: (v) => UPI_RE.test(v.trim()) || 'Enter a UPI ID such as name@bank' }} />
                <FormField label="Payee name (optional)" name="upiPayeeName" register={register} errors={errors} />
                <div className="mb-3">
                  <div className="form-label">Static QR code (optional)</div>
                  {data?.qrFileId && (
                    <div className="mb-2">
                      <AuthImage src={`/api/files/${data.qrFileId}`} alt="Your UPI QR code" className="border rounded"
                        fallback={<span className="small text-muted-hh">Loading QR…</span>} />
                    </div>
                  )}
                  {editable && (
                    <div className="d-flex gap-2 flex-wrap">
                      <input ref={fileRef} type="file" accept="image/png,image/jpeg,image/webp" className="form-control form-control-sm" style={{ maxWidth: 260 }}
                        aria-label="Upload UPI QR image" onChange={(e) => uploadQr(e.target.files[0])} />
                      {data?.qrFileId && <button type="button" className="btn btn-outline-danger btn-sm" onClick={removeQr}>Remove QR</button>}
                    </div>
                  )}
                  <div className="form-text">A QR only shows where to send money. It cannot tell you who paid or for which invoice, so payments made this way are not reconciled automatically.</div>
                </div>
              </div>
            )}
            {check('acceptsBankTransfer', 'Bank transfer (manual)', 'Residents transfer to your account. You mark the payment received yourself.')}
            {bank && (
              <div className="ps-4 mb-2">
                <FormField label="Account holder name" name="bankAccountHolder" register={register} errors={errors} rules={{ required: 'Enter the account holder name' }} />
                <div className="row g-0 gx-md-3">
                  <FormField className="col-md-6" label="Bank name" name="bankName" register={register} errors={errors} />
                  <FormField className="col-md-6" label="Branch" name="bankBranch" register={register} errors={errors} />
                  <FormField className="col-md-6" label="IFSC" name="bankIfsc" register={register} errors={errors}
                    rules={{ required: 'Enter the IFSC', validate: (v) => IFSC_RE.test(v.trim().toUpperCase()) || 'Enter an 11-character IFSC such as HDFC0001234' }} />
                </div>
                <FormField label={data?.hasBankAccount && !removeAccount ? `Account number (saved: ${data.bankAccountMasked}; leave blank to keep)` : 'Account number'}
                  name="bankAccountNumber" inputMode="numeric" autoComplete="off" register={register} errors={errors}
                  rules={{
                    validate: (v) => (!v.trim() ? (data?.hasBankAccount && !removeAccount) || 'Enter the account number' : ACCOUNT_RE.test(v.trim()) || 'Account number must be 9 to 18 digits'),
                  }}
                  help="Stored encrypted. It is shown in full only to residents paying an invoice and to staff who can view payments." />
                {data?.hasBankAccount && (
                  <div className="form-check mb-2">
                    <input id="pm-remove-account" type="checkbox" className="form-check-input" checked={removeAccount} onChange={(e) => setRemoveAccount(e.target.checked)} />
                    <label htmlFor="pm-remove-account" className="form-check-label">Remove the saved account number</label>
                  </div>
                )}
              </div>
            )}
            <FormField label="Extra instructions (optional)" name="instructions" as="textarea" register={register} errors={errors}
              help="Shown to residents with the payment details, e.g. “Pay by the 5th and send the screenshot to the warden.”" />
          </fieldset>
          {editable && (
            <button type="submit" className="btn btn-primary" disabled={isSubmitting || (!isDirty && !removeAccount)}>
              {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}Save payment methods
            </button>
          )}
          {!editable && <div className="small text-muted-hh">Only users with the “Manage settings” permission can change these.</div>}
        </form>
      </AsyncContent>
    </section>
  );
}

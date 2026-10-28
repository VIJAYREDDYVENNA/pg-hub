import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import useApi from '../hooks/useApi';
import { settingsApi, whatsappApi } from '../services/endpoints';
import { errorMessage } from '../services/apiClient';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import PageHeader from '../components/common/PageHeader';
import { PlanUsageCard } from '../components/subscription/UsagePanel';
import { AsyncContent, ErrorState, LoadingState } from '../components/common/States';
import FormField, { applyServerErrors } from '../components/common/FormField';
import WhatsAppModeBanner from '../components/operations/WhatsAppModeBanner';
import Icon from '../components/common/Icon';
import NotificationPreferences from '../components/operations/NotificationPreferences';
import PaymentMethodsCard from '../components/paymentmethods/PaymentMethodsCard';

const int = (min, max, what) => ({
  required: `Enter ${what}`,
  valueAsNumber: true,
  min: { value: min, message: `Minimum ${min}` },
  max: { value: max, message: `Maximum ${max}` },
  validate: (v) => Number.isInteger(v) || 'Enter a whole number',
});

function SettingsForm() {
  const { can } = useAuth();
  const { notify } = useToast();
  const editable = can('SETTINGS_MANAGE');
  const { data, setData, loading, error, reload } = useApi(() => settingsApi.get(), []);
  const [serverError, setServerError] = useState(null);
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting, isDirty } } = useForm();
  useEffect(() => { if (data) reset(data); }, [data, reset]);

  const onSubmit = async (v) => {
    setServerError(null);
    try {
      const saved = await settingsApi.update({ ...data, ...v, lateFeeAmount: Number(v.lateFeeAmount) });
      setData(saved);
      notify('Settings saved');
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not save the settings.'));
    }
  };

  return (
    <section className="hh-card h-100" aria-labelledby="settings-title">
      <div className="hh-card-header">
        <h2 className="hh-card-title" id="settings-title"><Icon className="bi bi-sliders me-2" />Business rules</h2>
        {!editable && <span className="badge hh-badge hh-badge-secondary"><Icon className="bi bi-lock me-1" />Read-only</span>}
      </div>
      <AsyncContent loading={loading && !data} error={error} onRetry={reload} compact>
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          {serverError && <div className="alert alert-danger py-2">{serverError}</div>}
          <fieldset disabled={!editable}>
            <div className="row g-0 gx-md-3">
              <FormField className="col-md-6" label="Vacancy warning (days)" name="vacancyWarningDays" type="number" inputMode="numeric" min="1" max="90"
                register={register} errors={errors} rules={int(1, 90, 'the number of days')}
                help="Beds are shown as “vacating soon” this many days before checkout." />
              <FormField className="col-md-6" label="Payment grace period (days)" name="paymentGraceDays" type="number" inputMode="numeric" min="0" max="60"
                register={register} errors={errors} rules={int(0, 60, 'the grace period')}
                help="Days after the due date before an invoice counts as overdue." />
              <FormField className="col-md-6" label="Late fee (₹)" name="lateFeeAmount" type="number" inputMode="decimal" min="0" step="0.01"
                register={register} errors={errors}
                rules={{ required: 'Enter the late fee (0 for none)', min: { value: 0, message: 'Cannot be negative' } }}
                help="Charged on overdue invoices; 0 disables late fees." />
              <FormField className="col-md-6" label="Reminder before due date (days)" name="reminderDaysBefore" type="number" inputMode="numeric" min="0" max="30"
                register={register} errors={errors} rules={int(0, 30, 'the number of days')}
                help="When automatic payment-due reminders are created." />
            </div>
            <div className="form-check mb-3">
              <input id="autoRemindersEnabled" type="checkbox" className="form-check-input" {...register('autoRemindersEnabled')} />
              <label htmlFor="autoRemindersEnabled" className="form-check-label">Send automatic WhatsApp rent reminders</label>
              <div className="form-text mt-0">
                Reminders go out the number of days before the due date set above, and again once an invoice is overdue, only to residents who
                agreed to WhatsApp messages. They stop as soon as the invoice is paid. WhatsApp is currently in mock mode unless configured below,
                so messages are only simulated.
              </div>
            </div>
            {data?.currencyCode && <div className="small text-muted-hh mb-3">Currency: {data.currencyCode}</div>}
          </fieldset>
          {editable && (
            <button type="submit" className="btn btn-primary" disabled={isSubmitting || !isDirty}>
              {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}Save settings
            </button>
          )}
          {!editable && <div className="small text-muted-hh">Only users with the “Manage settings” permission can change these.</div>}
        </form>
      </AsyncContent>
    </section>
  );
}

function WhatsAppCard() {
  const { data, loading, error, reload } = useApi(() => whatsappApi.status(), []);
  const webhook = `${window.location.origin}/api/whatsapp/webhook`;
  return (
    <section className="hh-card h-100" aria-labelledby="wa-title">
      <h2 className="hh-card-title mb-3" id="wa-title"><Icon className="bi bi-whatsapp me-2 text-success" />WhatsApp integration</h2>
      {loading && !data && <LoadingState compact />}
      {error && <ErrorState compact error={error} onRetry={reload} />}
      <WhatsAppModeBanner status={data} />
      <details className="small" open={data && data.mode === 'META' && !data.configured}>
        <summary className="fw-semibold mb-2">How to connect the WhatsApp Cloud API</summary>
        <ol className="ps-3 mb-2">
          <li>Create a Meta Business app with the WhatsApp product and add a phone number.</li>
          <li>Create and get approval for message templates (e.g. <code>rent_reminder</code>) in WhatsApp Manager, then set their names under WhatsApp → Templates.</li>
          <li>Set these environment variables on the backend server and restart it:
            <ul className="mt-1">
              <li><code>WHATSAPP_MODE=META</code></li>
              <li><code>WHATSAPP_PHONE_NUMBER_ID</code> — the phone number ID</li>
              <li><code>WHATSAPP_ACCESS_TOKEN</code> — a permanent system-user token</li>
              <li><code>WHATSAPP_APP_SECRET</code> — used to verify webhook signatures</li>
              <li><code>WHATSAPP_WEBHOOK_VERIFY_TOKEN</code> — any secret string you choose</li>
            </ul>
          </li>
          <li>In the Meta app, set the webhook callback URL to <code className="text-break">{webhook}</code> (path <code>/api/whatsapp/webhook</code>), use the same verify token and subscribe to <code>messages</code> to receive delivery/read statuses.</li>
        </ol>
        <div className="text-muted-hh">Secrets are never entered or stored in this app. Without these settings the system runs in mock mode and every message is shown as SIMULATED.</div>
      </details>
    </section>
  );
}

export default function SettingsPage() {
  useEffect(() => { document.title = 'Settings · HostelHub'; }, []);
  return (
    <>
      <PageHeader title="Settings" icon="bi-gear" subtitle="Business rules and integrations." />
      <div className="row g-3 mb-3">
        <div className="col-lg-6"><SettingsForm /></div>
        <div className="col-lg-6"><WhatsAppCard /></div>
        <div className="col-lg-6"><PaymentMethodsCard /></div>
        <div className="col-lg-6"><NotificationPreferences /></div>
        <div className="col-lg-6"><PlanUsageCard /></div>
      </div>
    </>
  );
}

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import useApi from '../../hooks/useApi';
import { adminApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/common/PageHeader';
import FormField, { applyServerErrors } from '../../components/common/FormField';
import { ErrorState, LoadingState } from '../../components/common/States';
import Icon from '../../components/common/Icon';
import { saCrumbs } from './saCommon';

/** Application-level settings kept in the platform_settings table. */
export default function SaSettingsPage() {
  const { notify } = useToast();
  const { data, loading, error, reload } = useApi(() => adminApi.settings(), []);
  const [serverError, setServerError] = useState(null);
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting, isDirty } } = useForm({
    defaultValues: { platformName: '', supportEmail: '' },
  });

  useEffect(() => {
    if (data) reset({ platformName: data.PLATFORM_NAME || '', supportEmail: data.SUPPORT_EMAIL || '' });
  }, [data, reset]);

  const onSubmit = async (v) => {
    setServerError(null);
    try {
      const saved = await adminApi.saveSettings({ platformName: v.platformName.trim(), supportEmail: v.supportEmail.trim() });
      reset({ platformName: saved.PLATFORM_NAME, supportEmail: saved.SUPPORT_EMAIL });
      notify('Application settings saved');
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not save the settings.'));
    }
  };

  const crumbs = saCrumbs({ label: 'Application Settings' });
  if (error) return <><PageHeader title="Application settings" icon="bi-gear-fill" breadcrumb={crumbs} /><ErrorState error={error} onRetry={reload} /></>;
  if (loading && !data) return <LoadingState />;

  return (
    <>
      <PageHeader title="Application settings" icon="bi-gear-fill" subtitle="Platform-wide configuration" breadcrumb={crumbs} />
      <form className="hh-card p-3 p-md-4" style={{ maxWidth: 720 }} onSubmit={handleSubmit(onSubmit)} noValidate>
        {serverError && <div className="alert alert-danger py-2" role="alert">{serverError}</div>}
        <FormField label="Platform name" name="platformName" register={register} errors={errors}
          rules={{ required: 'Enter the platform name', maxLength: { value: 100, message: 'Too long' } }} />
        <FormField label="Support email" name="supportEmail" type="email" register={register} errors={errors}
          rules={{ required: 'Enter the support email', pattern: { value: /^\S+@\S+\.\S+$/, message: 'Enter a valid email' } }}
          help="Where owners are told to reach the platform administrator." />
        <div className="alert alert-light border small d-flex gap-2" role="note">
          <Icon className="bi bi-info-circle mt-1" />
          <span>These values are stored on the platform and returned by the admin API. Sign-in security (lockout, token lifetimes) is configured on the server through environment variables, not here.</span>
        </div>
        <div className="d-flex justify-content-end">
          <button type="submit" className="btn btn-primary" disabled={isSubmitting || !isDirty}>
            {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}Save settings
          </button>
        </div>
      </form>
    </>
  );
}

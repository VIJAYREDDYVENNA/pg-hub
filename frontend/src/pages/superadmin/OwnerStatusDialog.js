import { useToast } from '../../context/ToastContext';
import { adminApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import ConfirmDialog from '../../components/common/ConfirmDialog';

const COPY = {
  ACTIVE: { title: 'Reactivate owner', verb: 'Reactivate', variant: 'success',
    text: (n) => `Reactivate ${n}? The owner and their managers will be able to sign in again.` },
  SUSPENDED: { title: 'Suspend owner', verb: 'Suspend', variant: 'warning',
    text: (n) => `Suspend ${n}? The owner and all their managers are signed out immediately and cannot sign in until the account is reactivated. No data is deleted.` },
  INACTIVE: { title: 'Deactivate owner', verb: 'Deactivate', variant: 'danger',
    text: (n) => `Deactivate ${n}? The owner and all their managers are signed out and cannot sign in. Hostels and records are kept; the account can be reactivated later.` },
};

/** Confirmation for activating / suspending / deactivating an owner account. `change` = { owner, status } | null. */
export default function OwnerStatusDialog({ change, onClose, onDone }) {
  const { notify } = useToast();
  if (!change) return null;
  const copy = COPY[change.status];
  const confirm = async (reason) => {
    try {
      const updated = await adminApi.setOwnerStatus(change.owner.id, change.status, reason);
      notify(`${change.owner.businessName}: ${copy.verb.toLowerCase()}d`);
      onDone?.(updated);
    } catch (e) {
      notify(errorMessage(e, 'Could not change the status.'), 'danger');
    }
  };
  return (
    <ConfirmDialog show title={copy.title} message={copy.text(change.owner.businessName)} confirmLabel={copy.verb}
      variant={copy.variant} requireReason={change.status !== 'ACTIVE'} reasonLabel="Reason (kept in the activity log)"
      onConfirm={confirm} onClose={onClose} />
  );
}

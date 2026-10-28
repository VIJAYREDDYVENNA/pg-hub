import { useEffect, useState } from 'react';
import Modal from '../common/Modal';
import ConfirmDialog from '../common/ConfirmDialog';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import { userApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { diffAssignments, managersByHostel, updateBody } from '../../pages/managers/managerCommon';
import HostelAssignPicker from './HostelAssignPicker';

/** Change a manager's hostel assignments, with a confirmation that spells out what is added and removed. */
export default function AssignHostelsModal({ manager, managers, onClose, onSaved }) {
  const { hostels } = useHostels();
  const { notify } = useToast();
  const [ids, setIds] = useState([]);
  const [confirm, setConfirm] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => { if (manager) { setIds(manager.hostelIds || []); setError(null); setConfirm(false); } }, [manager]);
  if (!manager) return null;

  const { added, removed } = diffAssignments(hostels, manager.hostelIds || [], ids);
  const changed = added.length + removed.length > 0;

  const review = () => {
    if (ids.length === 0) { setError('A manager needs at least one hostel. To remove all access, deactivate the manager instead.'); return; }
    setError(null);
    setConfirm(true);
  };
  const save = async () => {
    try {
      const saved = await userApi.update(manager.id, updateBody(manager, { hostelIds: ids }));
      notify(`Hostel assignments updated for ${manager.fullName}`);
      setConfirm(false);
      onSaved?.(saved);
    } catch (e) {
      setConfirm(false);
      setError(errorMessage(e, 'Could not update the assignments.'));
    }
  };

  const summary = (
    <>
      <span className="d-block mb-2">Update hostel access for <strong>{manager.fullName}</strong>?</span>
      {added.length > 0 && <span className="d-block small"><strong>Gains access to:</strong> {added.join(', ')}</span>}
      {removed.length > 0 && <span className="d-block small"><strong>Loses access to:</strong> {removed.join(', ')}. The change applies immediately.</span>}
    </>
  );

  return (
    <>
      <Modal show title={`Change hostel assignments · ${manager.fullName}`} onClose={onClose} size="lg"
        footer={<>
          <button type="button" className="btn btn-light" onClick={onClose}>Cancel</button>
          <button type="button" className="btn btn-primary" onClick={review} disabled={!changed}>Review &amp; save</button>
        </>}>
        {error && <div className="alert alert-danger py-2" role="alert">{error}</div>}
        <HostelAssignPicker hostels={hostels} value={ids} onChange={setIds} managerNames={managersByHostel(managers || [], manager.id)} />
      </Modal>
      <ConfirmDialog show={confirm} title="Confirm assignment changes" message={summary} confirmLabel="Save assignments"
        onConfirm={save} onClose={() => setConfirm(false)} />
    </>
  );
}

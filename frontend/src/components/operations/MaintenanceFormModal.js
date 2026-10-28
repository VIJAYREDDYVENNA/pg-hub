import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import Modal from '../common/Modal';
import FormField, { applyServerErrors } from '../common/FormField';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import { maintenanceApi, staffApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { label } from '../../utils/format';
import useRoomsForHostel from './useRoomsForHostel';
import { MAINTENANCE_CATEGORIES, PRIORITY_META, numOrNull, orNull } from './constants';
import Icon from '../common/Icon';

/** Loads staff for a hostel (active staff for assignment). */
export function useStaffForHostel(hostelId, enabled = true) {
  const [staff, setStaff] = useState([]);
  useEffect(() => {
    if (!enabled) return undefined;
    let alive = true;
    staffApi.list(hostelId || undefined).then((s) => { if (alive) setStaff(s || []); }).catch(() => { if (alive) setStaff([]); });
    return () => { alive = false; };
  }, [hostelId, enabled]);
  return staff;
}

/**
 * Create / edit a maintenance request.
 * request: existing request (edit) or null; prefill: { hostelId, roomId, bedId } for new requests.
 */
export default function MaintenanceFormModal({ show, request, prefill, onClose, onSaved }) {
  const { hostels, selectedHostelId } = useHostels();
  const { notify } = useToast();
  const editing = !!request?.id;
  const [hostelId, setHostelId] = useState(null);
  const [serverError, setServerError] = useState(null);
  const { register, handleSubmit, reset, watch, setValue, setError, formState: { errors, isSubmitting } } = useForm();
  const { rooms, loading: roomsLoading } = useRoomsForHostel(show ? hostelId : null);
  const staff = useStaffForHostel(hostelId, show && !!hostelId);

  useEffect(() => {
    if (!show) return;
    setServerError(null);
    const r = request || {};
    const p = prefill || {};
    setHostelId(r.hostelId || p.hostelId || selectedHostelId || hostels[0]?.id || null);
    reset({
      roomId: r.roomId ?? p.roomId ?? '', bedId: r.bedId ?? p.bedId ?? '', title: r.title || '', description: r.description || '',
      category: r.category || '', priority: r.priority || 'MEDIUM', blocksAvailability: !!r.blocksAvailability,
      estimatedCost: r.estimatedCost ?? '', assignedStaffId: r.assignedStaffId ?? '',
    });
  }, [show, request, prefill, selectedHostelId, hostels, reset]);

  const roomId = watch('roomId');
  const room = useMemo(() => rooms.find((r) => String(r.id) === String(roomId)), [rooms, roomId]);
  // Re-apply the room/bed values once the rooms arrive so the selects show them.
  useEffect(() => {
    if (!show || rooms.length === 0) return;
    const r = request || prefill || {};
    if (r.roomId && !roomId) setValue('roomId', String(r.roomId));
    if (r.bedId) setValue('bedId', String(r.bedId));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rooms]);

  const onSubmit = async (v) => {
    setServerError(null);
    const body = {
      hostelId,
      roomId: numOrNull(v.roomId),
      bedId: v.roomId ? numOrNull(v.bedId) : null,
      title: v.title.trim(),
      description: orNull(v.description),
      category: orNull(v.category),
      priority: v.priority,
      blocksAvailability: !!v.blocksAvailability,
      estimatedCost: numOrNull(v.estimatedCost),
      assignedStaffId: numOrNull(v.assignedStaffId),
    };
    try {
      const saved = editing ? await maintenanceApi.update(request.id, body) : await maintenanceApi.create(body);
      notify(editing ? 'Maintenance request updated' : 'Maintenance request created');
      onSaved?.(saved);
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not save the request.'));
    }
  };

  const staffOptions = staff.filter((s) => s.status === 'ACTIVE' || String(s.id) === String(request?.assignedStaffId));

  return (
    <Modal show={show} title={editing ? 'Edit maintenance request' : 'New maintenance request'} onClose={onClose} size="lg"
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="maint-form" className="btn btn-primary" disabled={isSubmitting || !hostelId}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}{editing ? 'Save changes' : 'Create request'}
        </button>
      </>}>
      <form id="maint-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        {serverError && <div className="alert alert-danger py-2" role="alert">{serverError}</div>}
        <div className="row g-0 gx-md-3">
          <div className="col-md-4 mb-3">
            <label className="form-label" htmlFor="maint-hostel">Hostel<span className="text-danger ms-1">*</span></label>
            <select id="maint-hostel" className="form-select" value={hostelId || ''} disabled={editing}
              onChange={(e) => { setHostelId(e.target.value ? Number(e.target.value) : null); setValue('roomId', ''); setValue('bedId', ''); setValue('assignedStaffId', ''); }}>
              {!hostelId && <option value="">Select hostel…</option>}
              {hostels.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
            </select>
            {editing && <div className="form-text">Requests cannot move to another hostel.</div>}
          </div>
          <FormField className="col-md-4" label="Room" name="roomId" as="select" register={register} errors={errors} disabled={!hostelId || roomsLoading}
            rules={{ onChange: () => setValue('bedId', '') }}
            options={[{ value: '', label: roomsLoading ? 'Loading…' : 'Common area / no room' },
              ...rooms.map((r) => ({ value: r.id, label: `Room ${r.roomNumber}${r.floorName ? ` · ${r.floorName}` : ''}` }))]} />
          <FormField className="col-md-4" label="Bed (optional)" name="bedId" as="select" register={register} errors={errors} disabled={!room}
            options={[{ value: '', label: 'Whole room' }, ...(room?.beds || []).map((b) => ({ value: b.id, label: `Bed ${b.label}` }))]} />
        </div>
        <FormField label="Title" name="title" register={register} errors={errors} placeholder="e.g. Leaking tap in bathroom"
          rules={{ required: 'Enter a short title', maxLength: { value: 150, message: 'Too long' } }} />
        <FormField label="Description" name="description" as="textarea" register={register} errors={errors}
          rules={{ maxLength: { value: 2000, message: 'Too long' } }} />
        <div className="row g-0 gx-md-3">
          <FormField className="col-md-4" label="Category" name="category" as="select" register={register} errors={errors}
            options={[{ value: '', label: 'Select…' }, ...MAINTENANCE_CATEGORIES.map((c) => ({ value: c, label: label(c) }))]} />
          <FormField className="col-md-4" label="Priority" name="priority" as="select" register={register} errors={errors} rules={{ required: 'Choose a priority' }}
            options={Object.entries(PRIORITY_META).map(([value, m]) => ({ value, label: m.label }))} />
          <FormField className="col-md-4" label="Estimated cost (₹)" name="estimatedCost" type="number" min="0" step="0.01" inputMode="decimal"
            register={register} errors={errors} rules={{ min: { value: 0, message: 'Cannot be negative' } }} />
        </div>
        <FormField label="Assign to staff" name="assignedStaffId" as="select" register={register} errors={errors} disabled={!hostelId}
          options={[{ value: '', label: 'Not assigned yet' }, ...staffOptions.map((s) => ({ value: s.id, label: `${s.fullName} · ${s.designation}` }))]}
          help={!editing ? 'Assigning someone sets the status to Assigned.' : undefined} />
        <div className="form-check p-3 ps-5 rounded-3 bg-primary-light">
          <input className="form-check-input" type="checkbox" id="f-blocksAvailability" {...register('blocksAvailability')} />
          <label className="form-check-label fw-semibold" htmlFor="f-blocksAvailability">
            <Icon className="bi bi-sign-stop me-1" aria-hidden="true" />Block availability
          </label>
          <div className="form-text">
            When ticked, the {watch('bedId') ? 'bed' : 'room'} cannot be allocated or reserved until this request is resolved.
            {!roomId && ' Select a room for this to have an effect.'}
          </div>
        </div>
      </form>
    </Modal>
  );
}

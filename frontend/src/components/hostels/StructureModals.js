import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import Modal from '../common/Modal';
import FormField, { applyServerErrors } from '../common/FormField';
import { bedApi, buildingApi, floorApi, roomApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useToast } from '../../context/ToastContext';
import { money } from '../../utils/format';

const blank = (v) => (v === undefined || v === null || String(v).trim() === '' ? null : String(v).trim());
const int = (v) => (v === undefined || v === null || String(v).trim() === '' ? null : parseInt(v, 10));
const MONEY_RULE = { pattern: { value: /^\d+(\.\d{1,2})?$/, message: 'Enter an amount like 5000 or 5000.50' } };

export const ROOM_TYPES = ['Single', 'Double', 'Triple', 'Four sharing', 'Dormitory', 'Deluxe', 'AC Double', 'AC Triple'];
const ACTIVE_OPTIONS = [{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }];
export const ROOM_CONFIG_STATUS = [
  { value: 'AVAILABLE', label: 'Available' },
  { value: 'MAINTENANCE', label: 'Under maintenance' },
  { value: 'INACTIVE', label: 'Inactive (hidden)' },
];

/**
 * Shared shell: a react-hook-form inside a Modal. `save(values)` performs the API call; server field errors
 * are mapped to fields and conflicts (409) are shown in the dialog and as a toast.
 */
function FormModal({ show, title, formId, defaults, save, onClose, onSaved, submitLabel = 'Save', successMessage, children }) {
  const { notify } = useToast();
  const [formError, setFormError] = useState(null);
  const form = useForm({ defaultValues: defaults });
  const { handleSubmit, reset, setError, formState: { isSubmitting } } = form;

  useEffect(() => { if (show) { reset(defaults); setFormError(null); } }, [show]); // eslint-disable-line react-hooks/exhaustive-deps

  const submit = async (values) => {
    setFormError(null);
    try {
      const result = await save(values);
      if (successMessage) notify(typeof successMessage === 'function' ? successMessage(values) : successMessage);
      onSaved?.(result);
    } catch (e) {
      const msg = errorMessage(e);
      if (!applyServerErrors(e, setError)) {
        setFormError(msg);
        if (e?.response?.status === 409) notify(msg, 'danger');
      } else setFormError('Please correct the highlighted fields.');
    }
  };

  return (
    <Modal show={show} title={title} onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form={formId} className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}{submitLabel}
        </button>
      </>}>
      <form id={formId} onSubmit={handleSubmit(submit)} noValidate>
        {formError && <div className="alert alert-danger py-2" role="alert">{formError}</div>}
        {children(form)}
      </form>
    </Modal>
  );
}

/** building = null to create. */
export function BuildingModal({ show, hostelId, building, onClose, onSaved }) {
  const defaults = building
    ? { name: building.name, description: building.description || '', status: building.status || 'ACTIVE', sortOrder: building.sortOrder ?? 0, numberOfFloors: '' }
    : { name: '', description: '', status: 'ACTIVE', sortOrder: '', numberOfFloors: 1 };
  const save = (v) => {
    const body = { hostelId, name: v.name.trim(), description: blank(v.description), status: v.status, sortOrder: int(v.sortOrder),
      numberOfFloors: building ? null : int(v.numberOfFloors) };
    return building ? buildingApi.update(building.id, body) : buildingApi.create(body);
  };
  return (
    <FormModal show={show} title={building ? `Edit ${building.name}` : 'Add building'} formId="building-form" defaults={defaults}
      save={save} onClose={onClose} onSaved={onSaved} submitLabel={building ? 'Save changes' : 'Add building'}
      successMessage={(v) => (building ? 'Building updated.' : `Building "${v.name}" added.`)}>
      {({ register, formState: { errors } }) => (
        <>
          <FormField label="Building name" name="name" register={register} errors={errors} placeholder="e.g. Block A"
            rules={{ required: 'Enter a name', maxLength: { value: 100, message: 'Max 100 characters' } }} />
          <FormField label="Description" name="description" as="textarea" register={register} errors={errors}
            rules={{ maxLength: { value: 500, message: 'Max 500 characters' } }} />
          <div className="row g-0 gx-sm-3">
            <div className="col-sm-6"><FormField label="Status" name="status" as="select" options={ACTIVE_OPTIONS} register={register} errors={errors} /></div>
            <div className="col-sm-6"><FormField label="Display order" name="sortOrder" type="number" inputMode="numeric" register={register} errors={errors} help="Lower numbers are shown first." /></div>
          </div>
          {!building && (
            <FormField label="Number of floors to create" name="numberOfFloors" type="number" min={0} max={100} inputMode="numeric"
              register={register} errors={errors} help="Floors are created as Ground, 1st, 2nd… You can rename or add more later."
              rules={{ min: { value: 0, message: 'Cannot be negative' }, max: { value: 100, message: 'At most 100 floors' } }} />
          )}
        </>
      )}
    </FormModal>
  );
}

/** floor = null to create. */
export function FloorModal({ show, buildingId, buildingName, floor, nextLevel = 0, onClose, onSaved }) {
  const defaults = floor
    ? { name: floor.name, levelNumber: floor.levelNumber, description: floor.description || '', sortOrder: floor.sortOrder ?? 0 }
    : { name: nextLevel === 0 ? 'Ground floor' : `Floor ${nextLevel}`, levelNumber: nextLevel, description: '', sortOrder: '' };
  const save = (v) => {
    const body = { buildingId, name: v.name.trim(), levelNumber: int(v.levelNumber), description: blank(v.description), sortOrder: int(v.sortOrder) };
    return floor ? floorApi.update(floor.id, body) : floorApi.create(body);
  };
  return (
    <FormModal show={show} title={floor ? `Edit ${floor.name}` : `Add floor${buildingName ? ` to ${buildingName}` : ''}`} formId="floor-form"
      defaults={defaults} save={save} onClose={onClose} onSaved={onSaved} submitLabel={floor ? 'Save changes' : 'Add floor'}
      successMessage={floor ? 'Floor updated.' : 'Floor added.'}>
      {({ register, formState: { errors } }) => (
        <>
          <FormField label="Floor name" name="name" register={register} errors={errors}
            rules={{ required: 'Enter a name', maxLength: { value: 60, message: 'Max 60 characters' } }} />
          <div className="row g-0 gx-sm-3">
            <div className="col-sm-6"><FormField label="Level number" name="levelNumber" type="number" inputMode="numeric" register={register} errors={errors}
              help="0 = ground, -1 = basement" rules={{ required: 'Enter the level number' }} /></div>
            <div className="col-sm-6"><FormField label="Display order" name="sortOrder" type="number" inputMode="numeric" register={register} errors={errors} /></div>
          </div>
          <FormField label="Description" name="description" as="textarea" register={register} errors={errors}
            rules={{ maxLength: { value: 500, message: 'Max 500 characters' } }} />
        </>
      )}
    </FormModal>
  );
}

/** room = structure room (null to create); floors = [{id, label}] for moving a room within the hostel. */
export function RoomModal({ show, floorId, floors = [], room, onClose, onSaved }) {
  const defaults = room
    ? { floorId: String(floorId), roomNumber: room.roomNumber, roomType: room.roomType, totalBeds: room.totalBeds,
      monthlyRentPerBed: room.monthlyRentPerBed ?? '', securityDeposit: room.securityDeposit ?? '', amenities: room.amenities || '', status: room.status || 'AVAILABLE' }
    : { floorId: String(floorId), roomNumber: '', roomType: 'Double', totalBeds: 2, monthlyRentPerBed: '', securityDeposit: '', amenities: '', status: 'AVAILABLE' };
  const save = (v) => {
    const body = {
      floorId: Number(v.floorId), roomNumber: v.roomNumber.trim(), roomType: v.roomType.trim(), totalBeds: int(v.totalBeds),
      monthlyRentPerBed: String(v.monthlyRentPerBed).trim(), securityDeposit: String(v.securityDeposit).trim(),
      amenities: blank(v.amenities), status: v.status,
    };
    return room ? roomApi.update(room.id, body) : roomApi.create(body);
  };
  return (
    <FormModal show={show} title={room ? `Edit room ${room.roomNumber}` : 'Add room'} formId="room-form" defaults={defaults}
      save={save} onClose={onClose} onSaved={onSaved} submitLabel={room ? 'Save changes' : 'Add room'}
      successMessage={(v) => (room ? `Room ${v.roomNumber} updated.` : `Room ${v.roomNumber} added with ${v.totalBeds} bed(s).`)}>
      {({ register, formState: { errors } }) => (
        <>
          <div className="row g-0 gx-sm-3">
            <div className="col-sm-6"><FormField label="Room number" name="roomNumber" register={register} errors={errors} placeholder="e.g. 101"
              rules={{ required: 'Enter the room number', maxLength: { value: 20, message: 'Max 20 characters' } }} /></div>
            <div className="col-sm-6">
              <FormField label="Room type" name="roomType" list="room-type-options" register={register} errors={errors}
                rules={{ required: 'Enter the room type', maxLength: { value: 30, message: 'Max 30 characters' } }} />
              <datalist id="room-type-options">{ROOM_TYPES.map((t) => <option key={t} value={t} />)}</datalist>
            </div>
            <div className="col-sm-4"><FormField label="Beds" name="totalBeds" type="number" min={1} max={30} inputMode="numeric" register={register} errors={errors}
              help={room ? 'Reducing removes free beds only.' : 'Beds are labelled A, B, C…'}
              rules={{ required: 'Enter the number of beds', min: { value: 1, message: 'At least 1 bed' }, max: { value: 30, message: 'At most 30 beds' } }} /></div>
            <div className="col-sm-4"><FormField label="Rent per bed / month (₹)" name="monthlyRentPerBed" inputMode="decimal" register={register} errors={errors}
              rules={{ required: 'Enter the rent', ...MONEY_RULE }} /></div>
            <div className="col-sm-4"><FormField label="Security deposit (₹)" name="securityDeposit" inputMode="decimal" register={register} errors={errors}
              rules={{ required: 'Enter the deposit (0 if none)', ...MONEY_RULE }} /></div>
          </div>
          <FormField label="Amenities" name="amenities" register={register} errors={errors} placeholder="e.g. Attached bath, AC, Balcony"
            rules={{ maxLength: { value: 500, message: 'Max 500 characters' } }} />
          <div className="row g-0 gx-sm-3">
            <div className="col-sm-6"><FormField label="Status" name="status" as="select" options={ROOM_CONFIG_STATUS} register={register} errors={errors}
              help="Rooms with residents cannot be made inactive." /></div>
            {room && floors.length > 1 && (
              <div className="col-sm-6"><FormField label="Floor" name="floorId" as="select" register={register} errors={errors}
                options={floors.map((f) => ({ value: String(f.id), label: f.label }))} /></div>
            )}
          </div>
        </>
      )}
    </FormModal>
  );
}

const BED_STATUS = [
  { value: 'AVAILABLE', label: 'Available' },
  { value: 'MAINTENANCE', label: 'Under maintenance' },
  { value: 'INACTIVE', label: 'Inactive (removed from use)' },
];

/** bed = BedView from room details (null to create). roomRent is the room's default rent per bed. */
export function BedModal({ show, roomId, roomRent, bed, onClose, onSaved }) {
  // Room details only expose the effective rent; it differs from the room rent only when an override is set.
  const hasOverride = bed && bed.rent !== null && bed.rent !== undefined && String(bed.rent) !== String(roomRent)
    && Number(bed.rent) !== Number(roomRent);
  const defaults = bed
    ? { bedLabel: bed.label, status: bed.bedStatus || (bed.maintenance ? 'MAINTENANCE' : 'AVAILABLE'), rentOverride: hasOverride ? String(bed.rent) : '' }
    : { bedLabel: '', status: 'AVAILABLE', rentOverride: '' };
  const save = (v) => {
    const body = { roomId, bedLabel: v.bedLabel.trim(), status: v.status, rentOverride: blank(v.rentOverride) };
    return bed ? bedApi.update(bed.id, body) : bedApi.create(body);
  };
  return (
    <FormModal show={show} title={bed ? `Edit bed ${bed.label}` : 'Add bed'} formId="bed-form" defaults={defaults} save={save}
      onClose={onClose} onSaved={onSaved} submitLabel={bed ? 'Save changes' : 'Add bed'}
      successMessage={bed ? 'Bed updated.' : 'Bed added.'}>
      {({ register, formState: { errors } }) => (
        <>
          <FormField label="Bed label" name="bedLabel" register={register} errors={errors} placeholder="e.g. A"
            rules={{ required: 'Enter a label', maxLength: { value: 20, message: 'Max 20 characters' } }} />
          <FormField label="Status" name="status" as="select" options={BED_STATUS} register={register} errors={errors}
            help="Occupied beds cannot be made inactive." />
          <FormField label="Rent override / month (₹)" name="rentOverride" inputMode="decimal" register={register} errors={errors}
            help={`Leave blank to use the room rent (${money(roomRent)}).`} rules={MONEY_RULE} />
        </>
      )}
    </FormModal>
  );
}

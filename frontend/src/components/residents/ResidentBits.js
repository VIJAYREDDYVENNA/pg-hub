import AuthImage from '../common/AuthImage';

export const RESIDENT_STATUS = {
  ACTIVE: { label: 'Staying', cls: 'success', icon: 'bi-house-check' },
  REGISTERED: { label: 'Registered', cls: 'info', icon: 'bi-person-plus' },
  CHECKED_OUT: { label: 'Checked out', cls: 'secondary', icon: 'bi-box-arrow-right' },
  INACTIVE: { label: 'Inactive', cls: 'secondary', icon: 'bi-slash-circle' },
};

export const ADMISSION_STATUS = {
  ACTIVE: { label: 'Active', cls: 'success', icon: 'bi-house-check' },
  CHECKED_OUT: { label: 'Checked out', cls: 'secondary', icon: 'bi-box-arrow-right' },
  CANCELLED: { label: 'Cancelled', cls: 'secondary', icon: 'bi-x-circle' },
};

export const GENDERS = [
  { value: '', label: 'Not specified' },
  { value: 'MALE', label: 'Male' },
  { value: 'FEMALE', label: 'Female' },
  { value: 'OTHER', label: 'Other' },
];

export const ID_TYPES = [
  { value: '', label: 'Not provided' },
  { value: 'AADHAAR', label: 'Aadhaar card' },
  { value: 'PAN', label: 'PAN card' },
  { value: 'PASSPORT', label: 'Passport' },
  { value: 'DRIVING_LICENCE', label: 'Driving licence' },
  { value: 'VOTER_ID', label: 'Voter ID' },
  { value: 'COLLEGE_ID', label: 'College / employee ID' },
  { value: 'OTHER', label: 'Other' },
];

export const initials = (name) => String(name || '?').trim().split(/\s+/).slice(0, 2).map((p) => p[0]).join('').toUpperCase();

/** Round photo with initials fallback. */
export function ResidentAvatar({ name, photoUrl, size = 38 }) {
  const style = { width: size, height: size, fontSize: size * 0.38, flexShrink: 0, overflow: 'hidden' };
  const fallback = <span aria-hidden="true">{initials(name)}</span>;
  return (
    <span className="hh-avatar" style={style}>
      {photoUrl ? <AuthImage src={photoUrl} alt={name} fallback={fallback} /> : fallback}
    </span>
  );
}

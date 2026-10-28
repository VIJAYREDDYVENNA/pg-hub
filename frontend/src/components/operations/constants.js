import { MAINTENANCE_STATUS, PRIORITY } from '../../utils/status';

/** Display metadata (label + icon + colour) for operations statuses. Colour is never the only cue. */


const MAINT_ICONS = {
  OPEN: 'bi-exclamation-circle', ASSIGNED: 'bi-person-gear', IN_PROGRESS: 'bi-wrench-adjustable',
  ON_HOLD: 'bi-pause-circle', RESOLVED: 'bi-check-circle-fill', CLOSED: 'bi-archive',
};
export const MAINT_STATUS = Object.fromEntries(Object.entries(MAINTENANCE_STATUS)
  .map(([k, v]) => [k, { icon: MAINT_ICONS[k], ...v }]));

const PRIORITY_ICONS = { LOW: 'bi-arrow-down', MEDIUM: 'bi-dash-lg', HIGH: 'bi-arrow-up', URGENT: 'bi-exclamation-triangle-fill' };
export const PRIORITY_META = Object.fromEntries(Object.entries(PRIORITY)
  .map(([k, v]) => [k, { icon: PRIORITY_ICONS[k], ...v }]));

export const STAFF_STATUS = {
  ACTIVE: { label: 'Active', cls: 'success', icon: 'bi-check-circle' },
  INACTIVE: { label: 'Inactive', cls: 'secondary', icon: 'bi-pause-circle' },
};

export const MAINTENANCE_CATEGORIES = ['PLUMBING', 'ELECTRICAL', 'FURNITURE', 'CLEANING', 'APPLIANCE', 'CARPENTRY',
  'PAINTING', 'INTERNET', 'PEST_CONTROL', 'OTHER'];
export const ROOM_TYPES = ['SINGLE', 'DOUBLE', 'TRIPLE', 'DORMITORY', 'AC', 'NON_AC'];

/** Keys of `{{placeholder}}` found in a template body, in order, without duplicates. */
export function placeholders(body) {
  const out = [];
  const re = /\{\{\s*(\w+)\s*\}\}/g;
  let m = re.exec(String(body || ''));
  while (m) {
    if (!out.includes(m[1])) out.push(m[1]);
    m = re.exec(String(body || ''));
  }
  return out;
}

/** Placeholders the server fills in itself. */
export const SERVER_FILLED = ['residentName', 'hostelName'];

/** Empty strings become null so optional numbers/dates/ids are sent as absent values. */
export const orNull = (v) => (v === '' || v === undefined ? null : v);
export const numOrNull = (v) => (v === '' || v === undefined || v === null ? null : Number(v));

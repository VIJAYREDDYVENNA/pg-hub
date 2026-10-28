/** Display metadata for statuses: colour token + icon + human label (colour is never the only cue). */
export const ROOM_STATUS = {
  FULLY_OCCUPIED: { label: 'Fully occupied', icon: 'bi-people-fill', cls: 'occupied' },
  PARTIALLY_OCCUPIED: { label: 'Partially occupied', icon: 'bi-person-fill', cls: 'partial' },
  VACANT: { label: 'Vacant', icon: 'bi-door-open', cls: 'vacant' },
  UNDER_MAINTENANCE: { label: 'Under maintenance', icon: 'bi-tools', cls: 'maintenance' },
  VACATING_SOON: { label: 'Vacating soon', icon: 'bi-hourglass-split', cls: 'soon' },
};

export const BED_STATE = {
  OCCUPIED: { label: 'Occupied', icon: 'bi-person-fill', cls: 'occupied' },
  AVAILABLE: { label: 'Available', icon: 'bi-check-circle', cls: 'vacant' },
  RESERVED: { label: 'Reserved', icon: 'bi-bookmark-fill', cls: 'reserved' },
  MAINTENANCE: { label: 'Maintenance', icon: 'bi-tools', cls: 'maintenance' },
};

export const INVOICE_STATUS = {
  PAID: { label: 'Paid', cls: 'success', icon: 'bi-check-circle-fill' },
  PARTIALLY_PAID: { label: 'Partially paid', cls: 'info', icon: 'bi-circle-half' },
  PENDING: { label: 'Pending', cls: 'warning', icon: 'bi-hourglass' },
  OVERDUE: { label: 'Overdue', cls: 'danger', icon: 'bi-exclamation-circle-fill' },
  WAIVED: { label: 'Waived', cls: 'secondary', icon: 'bi-slash-circle' },
  CANCELLED: { label: 'Cancelled', cls: 'secondary', icon: 'bi-x-circle' },
  REFUNDED: { label: 'Refunded', cls: 'secondary', icon: 'bi-arrow-counterclockwise' },
  RECEIVED: { label: 'Received', cls: 'success', icon: 'bi-check-circle-fill' },
  REVERSED: { label: 'Reversed', cls: 'danger', icon: 'bi-arrow-counterclockwise' },
};

export const PAYMENT_METHODS = [
  { value: 'UPI', label: 'UPI' },
  { value: 'CASH', label: 'Cash' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'CARD', label: 'Card' },
  { value: 'OTHER', label: 'Other' },
];

/** Gateway checkout attempts (distinct from invoice status and from the resulting payment). */
export const ATTEMPT_STATUS = {
  CREATED: { label: 'Not completed', cls: 'secondary', icon: 'bi-clock' },
  PENDING: { label: 'Awaiting bank', cls: 'warning', icon: 'bi-hourglass-split' },
  FAILED: { label: 'Failed', cls: 'danger', icon: 'bi-x-octagon' },
  SUCCEEDED: { label: 'Paid', cls: 'success', icon: 'bi-check-circle-fill' },
  EXPIRED: { label: 'Expired', cls: 'secondary', icon: 'bi-slash-circle' },
  MISMATCH: { label: 'Needs review', cls: 'danger', icon: 'bi-exclamation-triangle-fill' },
};

export const MESSAGE_STATUS = {
  QUEUED: { label: 'Queued', cls: 'secondary', icon: 'bi-clock' },
  SENT: { label: 'Sent', cls: 'info', icon: 'bi-check' },
  DELIVERED: { label: 'Delivered', cls: 'success', icon: 'bi-check-all' },
  READ: { label: 'Read', cls: 'success', icon: 'bi-eye' },
  FAILED: { label: 'Failed', cls: 'danger', icon: 'bi-x-octagon' },
  SKIPPED_NO_CONSENT: { label: 'Not sent – no consent', cls: 'warning', icon: 'bi-shield-exclamation' },
};

export const MAINTENANCE_STATUS = {
  OPEN: { label: 'Open', cls: 'danger' },
  ASSIGNED: { label: 'Assigned', cls: 'info' },
  IN_PROGRESS: { label: 'In progress', cls: 'warning' },
  ON_HOLD: { label: 'On hold', cls: 'secondary' },
  RESOLVED: { label: 'Resolved', cls: 'success' },
  CLOSED: { label: 'Closed', cls: 'secondary' },
};

export const PRIORITY = {
  LOW: { label: 'Low', cls: 'secondary' },
  MEDIUM: { label: 'Medium', cls: 'info' },
  HIGH: { label: 'High', cls: 'warning' },
  URGENT: { label: 'Urgent', cls: 'danger' },
};

/** What an owner may currently do, derived by the server from the subscription dates and status. */
export const SUBSCRIPTION_STATE = {
  TRIAL: { label: 'Trial', cls: 'info', icon: 'bi-hourglass-split' },
  ACTIVE: { label: 'Active', cls: 'success', icon: 'bi-check-circle-fill' },
  GRACE: { label: 'Grace period', cls: 'warning', icon: 'bi-exclamation-triangle-fill' },
  RESTRICTED: { label: 'Restricted', cls: 'danger', icon: 'bi-slash-circle' },
};

export const ENQUIRY_STATUS = {
  NEW: { label: 'New', cls: 'info', icon: 'bi-chat-dots' },
  CONTACTED: { label: 'Contacted', cls: 'info', icon: 'bi-telephone' },
  FOLLOW_UP: { label: 'Follow up', cls: 'warning', icon: 'bi-calendar-event' },
  BOOKED: { label: 'Bed reserved', cls: 'success', icon: 'bi-bookmark-fill' },
  CONVERTED: { label: 'Became resident', cls: 'success', icon: 'bi-person-check' },
  LOST: { label: 'Lost', cls: 'secondary', icon: 'bi-x-circle' },
};

export const BOOKING_STATUS = {
  RESERVED: { label: 'Reserved', cls: 'info', icon: 'bi-bookmark-fill' },
  CONVERTED: { label: 'Admitted', cls: 'success', icon: 'bi-person-check' },
  EXPIRED: { label: 'Expired', cls: 'secondary', icon: 'bi-hourglass-split' },
  CANCELLED: { label: 'Cancelled', cls: 'secondary', icon: 'bi-x-circle' },
};

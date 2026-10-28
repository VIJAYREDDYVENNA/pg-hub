import api from './apiClient';

const clean = (params) => Object.fromEntries(
  Object.entries(params || {}).filter(([, v]) => v !== undefined && v !== null && v !== '' && v !== 'ALL')
);
const get = (url, params) => api.get(url, { params: clean(params) }).then((r) => r.data);
const post = (url, body, params) => api.post(url, body, { params: clean(params) }).then((r) => r.data);
const put = (url, body) => api.put(url, body).then((r) => r.data);
const del = (url) => api.delete(url).then((r) => r.data);
const upload = (url, file, params) => {
  const fd = new FormData();
  fd.append('file', file);
  return api.post(url, fd, { params: clean(params), headers: { 'Content-Type': 'multipart/form-data' } }).then((r) => r.data);
};

export const authApi = {
  login: (email, password) => post('/auth/login', { email, password }),
  logout: () => post('/auth/logout'),
  me: () => get('/auth/me'),
  updateProfile: (body) => put('/auth/me', body),
  forgotPassword: (email) => post('/auth/forgot-password', { email }),
  resetPassword: (token, newPassword) => post('/auth/reset-password', { token, newPassword }),
  changePassword: (currentPassword, newPassword) => post('/auth/change-password', { currentPassword, newPassword }),
};

export const dashboardApi = {
  summary: (hostelId) => get('/dashboard/summary', { hostelId }),
  vacancies: (params) => get('/dashboard/vacancies', params),
  activity: (hostelId) => get('/dashboard/activity', { hostelId }),
  search: (q) => get('/search', { q }),
};

export const hostelApi = {
  list: () => get('/hostels'),
  get: (id) => get(`/hostels/${id}`),
  create: (body) => post('/hostels', body),
  update: (id, body) => put(`/hostels/${id}`, body),
  remove: (id) => del(`/hostels/${id}`),
  uploadImage: (id, file) => upload(`/hostels/${id}/image`, file),
  structure: (id) => get(`/hostels/${id}/structure`),
  buildingView: (id, buildingId, floorId) => get(`/hostels/${id}/building-view`, { buildingId, floorId }),
};

export const buildingApi = {
  list: (hostelId) => get('/buildings', { hostelId }),
  create: (body) => post('/buildings', body),
  update: (id, body) => put(`/buildings/${id}`, body),
  remove: (id) => del(`/buildings/${id}`),
};

export const floorApi = {
  list: (buildingId) => get('/floors', { buildingId }),
  create: (body) => post('/floors', body),
  update: (id, body) => put(`/floors/${id}`, body),
  remove: (id) => del(`/floors/${id}`),
  saveLayout: (id, body) => put(`/floors/${id}/layout`, body),
  reorder: (buildingId, floorIds) => api.put('/floors/order', { floorIds }, { params: { buildingId } }).then((r) => r.data),
};

export const roomApi = {
  list: (params) => get('/rooms', params),
  get: (id) => get(`/rooms/${id}`),
  create: (body) => post('/rooms', body),
  update: (id, body) => put(`/rooms/${id}`, body),
  remove: (id) => del(`/rooms/${id}`),
};

export const bedApi = {
  create: (body) => post('/beds', body),
  update: (id, body) => put(`/beds/${id}`, body),
  remove: (id) => del(`/beds/${id}`),
};

export const residentApi = {
  list: (params) => get('/residents', params),
  get: (id) => get(`/residents/${id}`),
  create: (body) => post('/residents', body),
  update: (id, body) => put(`/residents/${id}`, body),
  consent: (id, whatsappOptIn) => put(`/residents/${id}/whatsapp-consent`, { whatsappOptIn }),
  deactivate: (id) => post(`/residents/${id}/deactivate`),
  uploadPhoto: (id, file) => upload(`/residents/${id}/photo`, file),
  uploadDocument: (id, file, category) => upload(`/residents/${id}/documents`, file, { category }),
};

export const admissionApi = {
  list: (params) => get('/admissions', params),
  get: (id) => get(`/admissions/${id}`),
  admit: (body) => post('/admissions', body),
  update: (id, body) => put(`/admissions/${id}`, body),
  checkout: (id, body) => post(`/admissions/${id}/checkout`, body),
  transfer: (id, body) => post(`/admissions/${id}/transfer`, body),
};



export const invoiceApi = {
  list: (params) => get('/invoices', params),
  get: (id) => get(`/invoices/${id}`),
  create: (body) => post('/invoices', body),
  generateRent: (hostelId) => post('/invoices/generate-rent', null, { hostelId }),
  adjust: (id, body) => post(`/invoices/${id}/adjustments`, body),
  waive: (id, reason) => post(`/invoices/${id}/waive`, { reason }),
  cancel: (id, reason) => post(`/invoices/${id}/cancel`, { reason }),
};

/** Online (gateway) collection. `forInvoice` / `forLink` expose the same shape so one PayPanel serves both flows. */
export const onlinePaymentApi = {
  config: () => get('/online-payments/config'),
  attempts: (invoiceId) => get(`/online-payments/invoices/${invoiceId}/attempts`),
  createLink: (invoiceId) => post(`/online-payments/invoices/${invoiceId}/links`),
  settleSimulated: (attemptId, outcome) => post(`/online-payments/mock/attempts/${attemptId}/settle`, null, { outcome }),
  refresh: (attemptId) => post(`/online-payments/attempts/${attemptId}/refresh`),
  forInvoice: (invoiceId) => ({
    createOrder: (amount) => post(`/online-payments/invoices/${invoiceId}/orders`, { amount }),
    confirm: (providerOrderId, fields) => post('/online-payments/confirm', { providerOrderId, fields }),
    refresh: (attemptId) => post(`/online-payments/attempts/${attemptId}/refresh`),
  }),
};

export const publicPayApi = {
  invoice: (token) => get(`/public/pay/${token}`),
  qrUrl: (token) => `/api/public/pay/${token}/qr`,
  forLink: (token) => ({
    createOrder: (amount) => post(`/public/pay/${token}/orders`, { amount }),
    confirm: (providerOrderId, fields) => post(`/public/pay/${token}/confirm`, { providerOrderId, fields }),
    refresh: (attemptId) => post(`/public/pay/${token}/attempts/${attemptId}/refresh`),
  }),
};

export const paymentApi = {
  list: (params) => get('/payments', params),
  summary: (hostelId) => get('/payments/summary', { hostelId }),
  get: (id) => get(`/payments/${id}`),
  collect: (body) => post('/payments', body),
  reverse: (id, reason) => post(`/payments/${id}/reverse`, { reason }),
  receipt: (id) => get(`/payments/${id}/receipt`),
  refunds: (params) => get('/payments/refunds', params),
  refund: (body) => post('/payments/refunds', body),
  adjustDeposit: (body) => post('/payments/deposit-adjustments', body),
};

export const expenseApi = {
  list: (params) => get('/expenses', params),
  byCategory: (params) => get('/expenses/by-category', params),
  create: (body) => post('/expenses', body),
  update: (id, body) => put(`/expenses/${id}`, body),
};

export const maintenanceApi = {
  list: (params) => get('/maintenance', params),
  get: (id) => get(`/maintenance/${id}`),
  create: (body) => post('/maintenance', body),
  update: (id, body) => put(`/maintenance/${id}`, body),
  changeStatus: (id, body) => post(`/maintenance/${id}/status`, body),
  uploadImage: (id, file) => upload(`/maintenance/${id}/images`, file),
};

export const staffApi = {
  list: (hostelId) => get('/staff', { hostelId }),
  create: (body) => post('/staff', body),
  update: (id, body) => put(`/staff/${id}`, body),
};

export const userApi = {
  list: () => get('/users'),
  roles: () => get('/users/roles'),
  permissions: () => get('/users/permissions'),
  create: (body) => post('/users', body),
  update: (id, body) => put(`/users/${id}`, body),
  resetPassword: (id, newPassword) => post(`/users/${id}/reset-password`, { newPassword }),
};

export const notificationApi = {
  list: (params) => get('/notifications', params),
  unreadCount: () => get('/notifications/unread-count'),
  read: (id) => post(`/notifications/${id}/read`),
  readAll: () => post('/notifications/read-all'),
  preferences: () => get('/notifications/preferences'),
  updatePreferences: (prefs) => put('/notifications/preferences', prefs),
};

export const announcementApi = {
  list: (params) => get('/announcements', params),
  create: (body) => post('/announcements', body),
};

export const whatsappApi = {
  status: () => get('/whatsapp/status'),
  messages: (params) => get('/whatsapp/messages', params),
  send: (body) => post('/whatsapp/send', body),
  invoiceReminder: (invoiceId) => post(`/whatsapp/invoice-reminders/${invoiceId}`),
  bulkReminders: (hostelId, includeDueToday) => post('/whatsapp/bulk-reminders', null, { hostelId, includeDueToday }),
  templates: () => get('/whatsapp/templates'),
  saveTemplate: (code, body) => put(`/whatsapp/templates/${code}`, body),
};

export const settingsApi = {
  get: () => get('/owners/settings'),
  update: (body) => put('/owners/settings', body),
  auditLogs: (params) => get('/audit-logs', params),
};

/** Platform administration (SUPER_ADMIN only; enforced by the backend). */
export const adminApi = {
  dashboard: () => get('/admin/dashboard'),
  owners: () => get('/admin/owners'),
  owner: (id) => get(`/admin/owners/${id}`),
  createOwner: (body) => post('/admin/owners', body),
  updateOwner: (id, body) => put(`/admin/owners/${id}`, body),
  setOwnerStatus: (id, status, reason) => put(`/admin/owners/${id}/status`, { status, reason }),
  resetOwnerPassword: (id, newPassword) => post(`/admin/owners/${id}/reset-password`, { newPassword }),
  hostels: () => get('/admin/hostels'),
  activity: (params) => get('/admin/activity', params),
  settings: () => get('/admin/settings'),
  saveSettings: (body) => put('/admin/settings', body),
  plans: () => get('/admin/plans'),
  planFeatures: () => get('/admin/plans/features'),
  createPlan: (body) => post('/admin/plans', body),
  updatePlan: (id, body) => put(`/admin/plans/${id}`, body),
  setPlanStatus: (id, status) => put(`/admin/plans/${id}/status`, { status }),
  ownerSubscription: (ownerId) => get(`/admin/owners/${ownerId}/subscription`),
  updateOwnerSubscription: (ownerId, body) => put(`/admin/owners/${ownerId}/subscription`, body),
  recordSubscriptionPayment: (ownerId, body) => post(`/admin/owners/${ownerId}/subscription/payments`, body),
};

/** The signed-in owner's or manager's own plan, limits and usage. */
export const subscriptionApi = {
  mine: () => get('/subscription'),
};

/** Enquiries (leads) and bed reservations. */
export const enquiryApi = {
  list: (params) => get('/enquiries', params),
  get: (id) => get(`/enquiries/${id}`),
  create: (body) => post('/enquiries', body),
  update: (id, body) => put(`/enquiries/${id}`, body),
  convert: (id, hostelId) => post(`/enquiries/${id}/convert`, { hostelId }),
};

export const bookingApi = {
  list: (params) => get('/bookings', params),
  create: (body) => post('/bookings', body),
  cancel: (id, reason) => post(`/bookings/${id}/cancel`, { reason }),
};

/** How an owner accepts rent. Omit hostelId for the owner default. */
export const paymentMethodApi = {
  get: (hostelId) => get('/payment-settings', { hostelId }),
  update: (hostelId, body) => api.put('/payment-settings', body, { params: clean({ hostelId }) }).then((r) => r.data),
  removeOverride: (hostelId) => api.delete('/payment-settings', { params: clean({ hostelId }) }).then((r) => r.data),
  uploadQr: (hostelId, file) => upload('/payment-settings/qr', file, { hostelId }),
  removeQr: (hostelId) => api.delete('/payment-settings/qr', { params: clean({ hostelId }) }).then((r) => r.data),
  instructions: (hostelId) => get('/payment-settings/instructions', { hostelId }),
};

import { useMemo } from 'react';
import useApi from '../../hooks/useApi';
import { userApi } from '../../services/endpoints';

export const MANAGER_STATUS = {
  true: { label: 'Active', cls: 'success', icon: 'bi-check-circle-fill' },
  false: { label: 'Inactive', cls: 'secondary', icon: 'bi-slash-circle' },
};

/** Loads the owner's logins and exposes the ones holding the MANAGER role. */
export function useManagers() {
  const api = useApi(() => userApi.list(), []);
  const managers = useMemo(() => (api.data || []).filter((u) => u.roles?.includes('MANAGER')), [api.data]);
  return { ...api, managers };
}

/** hostelId -> names of the managers assigned to it (optionally leaving one manager out). */
export function managersByHostel(managers, excludeId) {
  const map = new Map();
  managers.filter((m) => m.id !== excludeId).forEach((m) => (m.hostelIds || []).forEach((h) => {
    map.set(h, [...(map.get(h) || []), m.fullName]);
  }));
  return map;
}

/** Request body for PUT /users/{id} that keeps everything except the overridden fields. */
export const updateBody = (m, over = {}) => ({
  fullName: m.fullName, phone: m.phone || '', roleCode: 'MANAGER', permissions: m.permissions || [],
  hostelIds: m.hostelIds || [], enabled: m.enabled, ...over,
});

/** Names of hostels added to / removed from an assignment, for the confirmation message. */
export function diffAssignments(hostels, before, after) {
  const name = (id) => hostels.find((h) => h.id === id)?.name || `Hostel #${id}`;
  const b = new Set(before);
  const a = new Set(after);
  return { added: [...a].filter((id) => !b.has(id)).map(name), removed: [...b].filter((id) => !a.has(id)).map(name) };
}

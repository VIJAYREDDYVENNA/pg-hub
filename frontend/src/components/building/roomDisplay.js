/**
 * Visual category of a room tile. The backend status is authoritative; this only decides the tile colour,
 * giving "vacating soon" and "maintenance" precedence so they stand out on the plan.
 */
export function roomCategory(room) {
  if (room.status === 'UNDER_MAINTENANCE') return 'maintenance';
  if (room.vacatingSoon) return 'soon';
  if (room.status === 'FULLY_OCCUPIED') return 'occupied';
  if (room.status === 'PARTIALLY_OCCUPIED') return 'partial';
  return 'vacant';
}

export const CATEGORY_META = {
  occupied: { label: 'Occupied', hint: 'All beds filled', color: '#22C55E' },
  partial: { label: 'Partially Occupied', hint: 'Some beds free', color: '#A7F3B5' },
  vacant: { label: 'Vacant', hint: 'No occupants', color: '#F87171' },
  soon: { label: 'Vacating Soon', hint: 'Checkout within warning period', color: '#FACC15' },
  maintenance: { label: 'Under Maintenance', hint: '', color: '#94A3B8' },
};

export function countCategories(rooms) {
  const c = { occupied: 0, partial: 0, vacant: 0, soon: 0, maintenance: 0 };
  (rooms || []).forEach((r) => { c[roomCategory(r)] += 1; });
  return c;
}

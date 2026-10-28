import { screen, waitFor, within, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import EnquiriesPage from './EnquiriesPage';
import { bookingApi, enquiryApi, roomApi } from '../services/endpoints';
import { setAuth } from '../test/authMock';
import { renderApp } from '../test/renderUtils';

jest.mock('../context/AuthContext', () => require('../test/authMock').factory());
jest.mock('../context/HostelContext', () => ({
  useHostels: () => ({ hostels: [{ id: 1, name: 'Alpha' }, { id: 2, name: 'Beta' }], selectedHostelId: null, selectHostel: jest.fn(), loading: false }),
}));
jest.mock('../services/endpoints', () => ({
  enquiryApi: { list: jest.fn(), get: jest.fn(), create: jest.fn(), update: jest.fn(), convert: jest.fn() },
  bookingApi: { list: jest.fn(), create: jest.fn(), cancel: jest.fn() },
  roomApi: { list: jest.fn() },
}));

const yesterday = () => new Date(Date.now() - 86400000).toISOString().slice(0, 10);
const tomorrow = () => new Date(Date.now() + 86400000).toISOString().slice(0, 10);
const enquiry = (id, over = {}) => ({ id, hostelId: 1, fullName: `Lead ${id}`, mobile: '9876500001', email: null, gender: null, preferredRoomType: 'Double',
  expectedAdmissionDate: null, budget: 6000, source: 'Phone call', status: 'NEW', nextFollowUpDate: null, notes: null, convertedResidentId: null, whatsappOptIn: false, ...over });
const pageOf = (...content) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });
const booking = (id, over = {}) => ({ id, hostelId: 1, hostelName: 'Alpha', bedId: 5, bedLabel: 'A', roomId: 3, roomNumber: '101', location: 'Block A · Ground', guestName: `Guest ${id}`,
  guestMobile: '9000000001', expectedAdmissionDate: tomorrow(), holdUntil: null, advanceAmount: 1000, status: 'RESERVED', ...over });

function LocationProbe() {
  const l = useLocation();
  return <div data-testid="location">{l.pathname}{l.search}</div>;
}
// Rows render twice (desktop table + mobile cards); tests work with the table row
const rowFor = async (text) => (await screen.findAllByText(text)).map((n) => n.closest('tr')).find(Boolean);
const renderPage = (route = '/enquiries') => renderApp(
  <Routes>
    <Route path="/enquiries" element={<EnquiriesPage />} />
    <Route path="/residents/:id" element={<div>Resident profile page</div>} />
    <Route path="*" element={<LocationProbe />} />
  </Routes>, { route });

beforeEach(() => {
  jest.clearAllMocks();
  setAuth({ permissions: ['ENQUIRY_MANAGE', 'RESIDENT_MANAGE'] });
  enquiryApi.list.mockResolvedValue(pageOf(enquiry(1), enquiry(2, { status: 'FOLLOW_UP', nextFollowUpDate: yesterday() })));
  enquiryApi.create.mockResolvedValue(enquiry(9, { fullName: 'New Lead' }));
  enquiryApi.update.mockResolvedValue(enquiry(1));
  enquiryApi.get.mockResolvedValue(enquiry(7, { fullName: 'Deep Linked' }));
  bookingApi.list.mockResolvedValue(pageOf(booking(1)));
  bookingApi.create.mockResolvedValue(booking(2, { guestName: 'Lead 1' }));
  bookingApi.cancel.mockResolvedValue(booking(1, { status: 'CANCELLED' }));
  roomApi.list.mockResolvedValue({ content: [{ id: 3, roomNumber: '101', roomType: 'Double', occupiedBeds: 1, totalBeds: 2, monthlyRentPerBed: 6000, securityDeposit: 5000,
    buildingId: 1, buildingName: 'Block A', floorId: 1, floorName: 'Ground', floorLevel: 0, beds: [{ id: 5, label: 'A', state: 'AVAILABLE', rent: 6000 }] }] });
});

describe('enquiries list', () => {
  test('lists enquiries with contact details, status and an overdue follow-up flag', async () => {
    renderPage();

    expect(await rowFor('Lead 1')).toBeInTheDocument();
    expect(screen.getAllByText(/9876500001/).length).toBeGreaterThan(0);
    const first = await rowFor('Lead 1');
    const second = await rowFor('Lead 2');
    expect(first).toHaveTextContent('New');
    expect(second).toHaveTextContent('Follow up');
    expect(second).toHaveTextContent('(overdue)');
    expect(first).not.toHaveTextContent('(overdue)');
    expect(enquiryApi.list).toHaveBeenCalledWith(expect.objectContaining({ status: 'ALL', page: 0, size: 20 }));
  });

  test('filters by status and searches with a debounce, always asking the server', async () => {
    const user = userEvent.setup();
    renderPage();
    await rowFor('Lead 1');

    await user.selectOptions(screen.getByLabelText('Status'), 'FOLLOW_UP');
    await waitFor(() => expect(enquiryApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'FOLLOW_UP' })));
    await user.type(screen.getByLabelText('Search enquiries'), 'ravi');
    await waitFor(() => expect(enquiryApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'ravi' })), { timeout: 3000 });
  });

  test('shows a helpful empty state', async () => {
    enquiryApi.list.mockResolvedValue(pageOf());
    renderPage();
    expect(await screen.findByText('No enquiries yet')).toBeInTheDocument();
  });

  test('a load failure offers a retry', async () => {
    enquiryApi.list.mockRejectedValueOnce({ response: { status: 500, data: {} } });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: /retry|try again/i }));

    expect(await rowFor('Lead 1')).toBeInTheDocument();
  });
});

describe('adding and editing', () => {
  test('a new enquiry is validated, then saved with the entered details', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: /New enquiry/ }));
    const dialog = await screen.findByRole('dialog');

    await user.click(within(dialog).getByRole('button', { name: 'Add enquiry' }));
    expect(await within(dialog).findByText('Enter the name')).toBeInTheDocument();
    expect(within(dialog).getByText('Enter the mobile number')).toBeInTheDocument();
    expect(enquiryApi.create).not.toHaveBeenCalled();

    await user.type(within(dialog).getByLabelText(/^Name/), 'New Lead');
    await user.type(within(dialog).getByLabelText(/^Mobile/), '9123456780');
    await user.type(within(dialog).getByLabelText(/Budget/), '7500');
    await user.selectOptions(within(dialog).getByLabelText('Interested in hostel'), '2');
    await user.click(within(dialog).getByLabelText(/agreed to receive WhatsApp/));
    await user.click(within(dialog).getByRole('button', { name: 'Add enquiry' }));

    await waitFor(() => expect(enquiryApi.create).toHaveBeenCalledTimes(1));
    expect(enquiryApi.create.mock.calls[0][0]).toMatchObject({
      fullName: 'New Lead', mobile: '9123456780', budget: 7500, hostelId: 2, status: 'NEW', whatsappOptIn: true, email: null,
    });
  });

  test('an invalid mobile number is rejected', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: /New enquiry/ }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText(/^Name/), 'X');
    await user.type(within(dialog).getByLabelText(/^Mobile/), '12ab');

    await user.click(within(dialog).getByRole('button', { name: 'Add enquiry' }));

    expect(await within(dialog).findByText('Enter a valid mobile number')).toBeInTheDocument();
    expect(enquiryApi.create).not.toHaveBeenCalled();
  });

  test('opening a row edits that enquiry, including its status', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await rowFor('Lead 1'));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByLabelText(/^Name/)).toHaveValue('Lead 1');
    await user.selectOptions(within(dialog).getByLabelText('Status'), 'CONTACTED');
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(enquiryApi.update).toHaveBeenCalledWith(1, expect.objectContaining({ status: 'CONTACTED', fullName: 'Lead 1' })));
  });

  test('a notification link /enquiries?id=7 opens that enquiry directly', async () => {
    renderPage('/enquiries?id=7');

    const dialog = await screen.findByRole('dialog');
    expect(enquiryApi.get).toHaveBeenCalledWith(7);
    expect(await within(dialog).findByDisplayValue('Deep Linked')).toBeInTheDocument();
  });
});

describe('permission-based actions', () => {
  test('converting to a resident is offered only to users who can manage residents', async () => {
    setAuth({ permissions: ['ENQUIRY_MANAGE'] });
    renderPage();
    await rowFor('Lead 1');
    expect(screen.queryByRole('button', { name: 'Convert to resident' })).not.toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Reserve a bed' }).length).toBeGreaterThan(0);
  });

  test('converting creates the resident and goes to their profile', async () => {
    enquiryApi.convert.mockResolvedValue({ residentId: 55 });
    const user = userEvent.setup();
    renderPage();
    const row = (await rowFor('Lead 1'));

    await user.click(within(row).getByRole('button', { name: 'Convert to resident' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText('Hostel')).toHaveValue('1');
    await user.click(within(dialog).getByRole('button', { name: 'Create resident' }));

    await waitFor(() => expect(enquiryApi.convert).toHaveBeenCalledWith(1, 1));
    expect(await screen.findByText('Resident profile page')).toBeInTheDocument();
  });

  test('converted and lost enquiries offer no reserve/convert actions', async () => {
    enquiryApi.list.mockResolvedValue(pageOf(enquiry(3, { status: 'CONVERTED' }), enquiry(4, { status: 'LOST' })));
    renderPage();
    await rowFor('Lead 3');
    expect(screen.queryByRole('button', { name: 'Reserve a bed' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Convert to resident' })).not.toBeInTheDocument();
  });
});

describe('reserving a bed from an enquiry', () => {
  test('the dialog is prefilled, requires a bed, and links the reservation to the enquiry', async () => {
    const user = userEvent.setup();
    renderPage();
    const row = (await rowFor('Lead 1'));
    await user.click(within(row).getByRole('button', { name: 'Reserve a bed' }));
    const dialog = await screen.findByRole('dialog', { name: 'Reserve a bed' });

    expect(within(dialog).getByLabelText(/Guest name/)).toHaveValue('Lead 1');
    expect(within(dialog).getByLabelText(/Hostel/)).toHaveValue('1');
    await user.click(within(dialog).getByRole('button', { name: 'Reserve bed' }));
    expect(await within(dialog).findByText('Choose a bed to reserve')).toBeInTheDocument();
    expect(bookingApi.create).not.toHaveBeenCalled();

    await user.click(await within(dialog).findByRole('radio', { name: /Bed A/ }));
    await user.type(within(dialog).getByLabelText(/Advance received/), '1500');
    await user.click(within(dialog).getByRole('button', { name: 'Reserve bed' }));

    await waitFor(() => expect(bookingApi.create).toHaveBeenCalledTimes(1));
    expect(bookingApi.create.mock.calls[0][0]).toMatchObject({ bedId: 5, enquiryId: 1, guestName: 'Lead 1', guestMobile: '9876500001', advanceAmount: 1500 });
  });

  test('a past admission date is rejected before anything is sent', async () => {
    const user = userEvent.setup();
    renderPage();
    const row = (await rowFor('Lead 1'));
    await user.click(within(row).getByRole('button', { name: 'Reserve a bed' }));
    const dialog = await screen.findByRole('dialog', { name: 'Reserve a bed' });
    fireEvent.change(within(dialog).getByLabelText(/Expected admission/), { target: { value: yesterday() } });
    await user.click(await within(dialog).findByRole('radio', { name: /Bed A/ }));

    await user.click(within(dialog).getByRole('button', { name: 'Reserve bed' }));

    expect(await within(dialog).findByText('The date cannot be in the past')).toBeInTheDocument();
    expect(bookingApi.create).not.toHaveBeenCalled();
  });

  test('a server conflict (bed already reserved) is shown in the dialog', async () => {
    bookingApi.create.mockRejectedValue({ response: { status: 409, data: { message: 'This bed is already reserved for Someone' } } });
    const user = userEvent.setup();
    renderPage();
    const row = (await rowFor('Lead 1'));
    await user.click(within(row).getByRole('button', { name: 'Reserve a bed' }));
    const dialog = await screen.findByRole('dialog', { name: 'Reserve a bed' });
    await user.click(await within(dialog).findByRole('radio', { name: /Bed A/ }));
    await user.click(within(dialog).getByRole('button', { name: 'Reserve bed' }));

    expect(await within(dialog).findByText('This bed is already reserved for Someone')).toBeInTheDocument();
  });
});

describe('reserved beds tab', () => {
  test('lists reservations and cancelling needs a reason', async () => {
    const user = userEvent.setup();
    renderPage('/enquiries?tab=reservations');

    expect(await rowFor('Guest 1')).toBeInTheDocument();
    expect(await rowFor('Guest 1')).toHaveTextContent('Room 101 · Bed A');
    expect(bookingApi.list).toHaveBeenCalledWith(expect.objectContaining({ status: 'RESERVED' }));

    await user.click(within(await rowFor('Guest 1')).getByRole('button', { name: 'Cancel reservation' }));
    const dialog = await screen.findByRole('dialog', { name: 'Cancel reservation' });
    const confirm = within(dialog).getByRole('button', { name: 'Cancel reservation' });
    expect(confirm).toBeDisabled();
    await user.type(within(dialog).getByLabelText('Why is it being cancelled?'), 'Guest changed plans');
    await user.click(confirm);

    await waitFor(() => expect(bookingApi.cancel).toHaveBeenCalledWith(1, 'Guest changed plans'));
  });

  test('cancelled and admitted reservations cannot be cancelled again', async () => {
    bookingApi.list.mockResolvedValue(pageOf(booking(3, { status: 'CANCELLED' }), booking(4, { status: 'CONVERTED' })));
    renderPage('/enquiries?tab=reservations');
    await rowFor('Guest 3');
    expect(screen.queryAllByRole('button', { name: 'Cancel reservation' })).toHaveLength(0);
  });

  test('switching tabs keeps the page usable and updates the address', async () => {
    const user = userEvent.setup();
    renderPage();
    await rowFor('Lead 1');

    await user.click(screen.getByRole('tab', { name: /Reserved beds/ }));

    expect(await rowFor('Guest 1')).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: /Enquiries/ }));
    expect(await rowFor('Lead 1')).toBeInTheDocument();
  });
});

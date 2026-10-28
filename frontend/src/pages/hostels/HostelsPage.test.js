import { screen, waitFor } from '@testing-library/react';
import HostelsPage from './HostelsPage';
import { subscriptionApi } from '../../services/endpoints';
import { setAuth } from '../../test/authMock';
import { mySubscription, renderApp } from '../../test/renderUtils';

jest.mock('../../context/AuthContext', () => require('../../test/authMock').factory());
jest.mock('../../context/HostelContext', () => ({
  useHostels: () => ({ hostels: [], loading: false, error: null, reload: jest.fn(), selectHostel: jest.fn() }),
}));
jest.mock('../../services/endpoints', () => ({
  subscriptionApi: { mine: jest.fn() },
  hostelApi: { create: jest.fn(), update: jest.fn(), remove: jest.fn(), structure: jest.fn() },
}));

const addButtons = () => screen.queryAllByRole('button', { name: /Add hostel/ });

beforeEach(() => {
  jest.clearAllMocks();
  setAuth({ permissions: ['HOSTEL_VIEW', 'HOSTEL_MANAGE'] });
  subscriptionApi.mine.mockResolvedValue(mySubscription());
});

test('shows "Hostels: used / limit" and keeps Add hostel enabled below the limit', async () => {
  renderApp(<HostelsPage />);

  expect(await screen.findByText('Hostels: 2 / 3')).toBeInTheDocument();
  expect(addButtons().length).toBeGreaterThan(0);
  addButtons().forEach((b) => expect(b).toBeEnabled());
});

test('at the limit, Add hostel is disabled and explains how to get more', async () => {
  subscriptionApi.mine.mockResolvedValue(mySubscription({
    usage: { hostels: 3, managers: 1, hostelBeds: [] },
    warnings: ['Hostels: 3 / 3. Limit reached; contact the platform administrator to upgrade.'],
  }));
  renderApp(<HostelsPage />);

  expect(await screen.findByText('Hostels: 3 / 3')).toBeInTheDocument();
  await waitFor(() => addButtons().forEach((b) => expect(b).toBeDisabled()));
  addButtons().forEach((b) => expect(b).toHaveAttribute('title', expect.stringContaining('Contact the platform administrator')));
  expect(screen.getByText('Hostel limit reached (3 / 3). Contact the platform administrator to upgrade your plan.')).toBeInTheDocument();
});

test('an inactive subscription disables Add hostel with a renewal message', async () => {
  subscriptionApi.mine.mockResolvedValue(mySubscription({
    accessState: 'RESTRICTED', canAddResources: false, warnings: ['Your subscription is not active.'],
  }));
  renderApp(<HostelsPage />);

  await waitFor(() => addButtons().forEach((b) => expect(b).toBeDisabled()));
  expect(screen.getAllByText(/subscription is not active/i).length).toBeGreaterThan(0);
});

test('a user without hostel-management permission sees no Add hostel button at all', async () => {
  setAuth({ permissions: ['HOSTEL_VIEW'] });
  renderApp(<HostelsPage />);

  await screen.findByText('Hostels: 2 / 3');
  expect(addButtons()).toHaveLength(0);
});

test('the page still works if the subscription summary cannot be loaded', async () => {
  subscriptionApi.mine.mockRejectedValue(new Error('down'));
  renderApp(<HostelsPage />);

  await waitFor(() => expect(subscriptionApi.mine).toHaveBeenCalled());
  expect(screen.getByRole('heading', { name: 'Hostels' })).toBeInTheDocument();
  addButtons().forEach((b) => expect(b).toBeEnabled());
});

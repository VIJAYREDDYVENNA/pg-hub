import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import NotificationPanel from './NotificationPanel';
import Topbar from './Topbar';
import { notificationApi } from '../../services/endpoints';
import { setAuth } from '../../test/authMock';

jest.mock('../../context/AuthContext', () => require('../../test/authMock').factory());
jest.mock('../../services/endpoints', () => ({
  notificationApi: { list: jest.fn(), read: jest.fn(), unreadCount: jest.fn(), readAll: jest.fn() },
  dashboardApi: { search: jest.fn().mockResolvedValue([]) },
}));

const page = (...items) => ({ content: items, totalElements: items.length });
const n = (id, over = {}) => ({ id, type: 'PAYMENT_DUE', title: `Title ${id}`, message: `Message ${id}`, read: false, link: null, createdAt: new Date().toISOString(), ...over });

const renderPanel = (props = {}) => {
  const onClose = jest.fn();
  const onCountChange = jest.fn();
  const utils = render(
    <MemoryRouter>
      <NotificationPanel show onClose={onClose} onCountChange={onCountChange} {...props} />
    </MemoryRouter>
  );
  return { ...utils, onClose, onCountChange };
};

beforeEach(() => {
  jest.clearAllMocks();
  setAuth({ owner: true, user: { id: 1, fullName: 'Owner One', roles: ['OWNER'] } });
  notificationApi.read.mockResolvedValue({});
  notificationApi.readAll.mockResolvedValue({});
  notificationApi.unreadCount.mockResolvedValue({ count: 0 });
});

describe('notification panel', () => {
  test('shows exactly what the server returns, flagging unread ones', async () => {
    notificationApi.list.mockResolvedValue(page(n(1), n(2, { read: true })));
    renderPanel();

    expect(await screen.findByText('Title 1')).toBeInTheDocument();
    expect(screen.getByText('Title 2')).toBeInTheDocument();
    expect(screen.getAllByText('NEW')).toHaveLength(1);
    expect(notificationApi.list).toHaveBeenCalledWith({ size: 30 });
  });

  test('after the server has deleted expired notifications, reopening shows only what remains', async () => {
    notificationApi.list.mockResolvedValueOnce(page(n(1), n(2), n(3)));
    const { rerender } = render(
      <MemoryRouter><NotificationPanel show onClose={() => {}} onCountChange={() => {}} /></MemoryRouter>
    );
    expect(await screen.findByText('Title 3')).toBeInTheDocument();

    // The 30-day cleanup ran on the server: only notification 3 is left
    notificationApi.list.mockResolvedValueOnce(page(n(3)));
    rerender(<MemoryRouter><NotificationPanel show={false} onClose={() => {}} onCountChange={() => {}} /></MemoryRouter>);
    rerender(<MemoryRouter><NotificationPanel show onClose={() => {}} onCountChange={() => {}} /></MemoryRouter>);

    await waitFor(() => expect(screen.queryByText('Title 1')).not.toBeInTheDocument());
    expect(screen.queryByText('Title 2')).not.toBeInTheDocument();
    expect(screen.getByText('Title 3')).toBeInTheDocument();
  });

  test('when everything has expired the panel says so rather than showing stale items', async () => {
    notificationApi.list.mockResolvedValue(page());
    renderPanel();

    expect(await screen.findByText('No notifications')).toBeInTheDocument();
    expect(screen.getByText('You are all caught up.')).toBeInTheDocument();
  });

  test('opening an unread notification marks it read and takes the badge count from the server', async () => {
    notificationApi.list.mockResolvedValue(page(n(1), n(2)));
    notificationApi.unreadCount.mockResolvedValue({ count: 1 });
    const user = userEvent.setup();
    const { onCountChange, onClose } = renderPanel();

    await user.click(await screen.findByText('Title 1'));

    expect(notificationApi.read).toHaveBeenCalledWith(1);
    await waitFor(() => expect(onCountChange).toHaveBeenCalledWith(1));
    expect(onClose).toHaveBeenCalled();
  });

  test('opening an already-read notification does not call the server again', async () => {
    notificationApi.list.mockResolvedValue(page(n(1, { read: true })));
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByText('Title 1'));

    expect(notificationApi.read).not.toHaveBeenCalled();
  });

  test('Mark all read zeroes the badge and reloads the list', async () => {
    notificationApi.list.mockResolvedValue(page(n(1), n(2)));
    const user = userEvent.setup();
    const { onCountChange } = renderPanel();
    await screen.findByText('Title 1');

    notificationApi.list.mockResolvedValue(page(n(1, { read: true }), n(2, { read: true })));
    await user.click(screen.getByRole('button', { name: /Mark all read/ }));

    await waitFor(() => expect(notificationApi.readAll).toHaveBeenCalled());
    expect(onCountChange).toHaveBeenCalledWith(0);
    await waitFor(() => expect(screen.queryAllByText('NEW')).toHaveLength(0));
  });

  test('a load error is reported with a retry', async () => {
    notificationApi.list.mockRejectedValueOnce({ response: { status: 500, data: {} } });
    notificationApi.list.mockResolvedValueOnce(page(n(1)));
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByRole('button', { name: /retry|try again/i }));

    expect(await screen.findByText('Title 1')).toBeInTheDocument();
  });
});

describe('notification badge in the top bar', () => {
  beforeEach(() => jest.useFakeTimers({ advanceTimers: true }));
  afterEach(() => jest.useRealTimers());

  const renderBar = () => render(<MemoryRouter><Topbar onMenu={() => {}} /></MemoryRouter>);

  test('shows the unread count and refreshes it every minute, so purged notifications leave the badge', async () => {
    notificationApi.unreadCount.mockResolvedValueOnce({ count: 7 }).mockResolvedValue({ count: 2 });
    renderBar();

    const bell = await screen.findByRole('button', { name: 'Notifications (7 unread)' });
    expect(bell).toHaveTextContent('7');

    await act(async () => { jest.advanceTimersByTime(60000); });

    expect(await screen.findByRole('button', { name: 'Notifications (2 unread)' })).toHaveTextContent('2');
  });

  test('no badge at all when nothing is unread, and 99+ for a large backlog', async () => {
    notificationApi.unreadCount.mockResolvedValue({ count: 0 });
    const { unmount } = renderBar();
    const bell = await screen.findByRole('button', { name: 'Notifications (0 unread)' });
    expect(bell.querySelector('.hh-dot')).toBeNull();
    unmount();

    notificationApi.unreadCount.mockResolvedValue({ count: 250 });
    renderBar();
    expect(await screen.findByText('99+')).toBeInTheDocument();
  });

  test('a failed count request leaves the previous badge in place instead of crashing', async () => {
    notificationApi.unreadCount.mockResolvedValueOnce({ count: 4 }).mockRejectedValue(new Error('offline'));
    renderBar();
    await screen.findByRole('button', { name: 'Notifications (4 unread)' });

    await act(async () => { jest.advanceTimersByTime(60000); });

    expect(screen.getByRole('button', { name: 'Notifications (4 unread)' })).toBeInTheDocument();
  });
});

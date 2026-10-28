import { render, screen, waitFor } from '@testing-library/react';
import { PlanUsageCard, SubscriptionBanner, SubscriptionNotice, UsageChip, bedAddStatus, hostelAddStatus } from './UsagePanel';
import { subscriptionApi } from '../../services/endpoints';
import { mySubscription } from '../../test/renderUtils';

jest.mock('../../services/endpoints', () => ({ subscriptionApi: { mine: jest.fn() } }));

beforeEach(() => {
  jest.clearAllMocks();
  subscriptionApi.mine.mockResolvedValue(mySubscription());
});

describe('UsageChip', () => {
  test('shows "label: used / limit"', () => {
    render(<UsageChip label="Hostels" used={2} limit={3} />);
    expect(screen.getByText('Hostels: 2 / 3')).toBeInTheDocument();
  });

  test.each([
    [10, 100, 'text-bg-light', null],
    [80, 100, 'text-bg-warning', null],
    [100, 100, 'text-bg-danger', 'Limit reached'],
    [104, 100, 'text-bg-danger', 'Above the limit'],
  ])('%i of %i uses %s', (used, limit, cls, title) => {
    const { container } = render(<UsageChip label="Beds" used={used} limit={limit} />);
    const chip = container.querySelector('.badge');
    expect(chip).toHaveClass(cls);
    if (title) expect(chip).toHaveAttribute('title', title);
    else expect(chip).not.toHaveAttribute('title');
  });

  test('a warning or full state carries an icon, so colour is not the only cue', () => {
    const { container: ok } = render(<UsageChip label="Beds" used={1} limit={100} />);
    const { container: full } = render(<UsageChip label="Beds" used={100} limit={100} />);
    expect(ok.querySelector('svg')).toBeNull();
    expect(full.querySelector('svg')).not.toBeNull();
  });

  test('honours a custom warning threshold', () => {
    const { container } = render(<UsageChip label="Beds" used={50} limit={100} warnPercent={50} />);
    expect(container.querySelector('.badge')).toHaveClass('text-bg-warning');
  });
});

describe('can the owner add another hostel or bed?', () => {
  test('hostels: allowed below the limit, blocked at it with the reason', () => {
    expect(hostelAddStatus(mySubscription()).blocked).toBe(false);
    const full = hostelAddStatus(mySubscription({ usage: { hostels: 3, managers: 0, hostelBeds: [] } }));
    expect(full.blocked).toBe(true);
    expect(full.message).toBe('Hostel limit reached (3 / 3). Contact the platform administrator to upgrade your plan.');
  });

  test('beds: the limit applies to the individual hostel', () => {
    const s = mySubscription({ usage: { hostels: 2, managers: 0, hostelBeds: [{ hostelId: 1, hostelName: 'A', beds: 100 }, { hostelId: 2, hostelName: 'B', beds: 10 }] } });
    expect(bedAddStatus(s, 1).blocked).toBe(true);
    expect(bedAddStatus(s, 1).message).toMatch(/Bed limit reached \(100 \/ 100\)/);
    expect(bedAddStatus(s, '2').blocked).toBe(false);
    expect(bedAddStatus(s, 99).blocked).toBe(false);
  });

  test('a restricted subscription blocks both, with a renewal message', () => {
    const s = mySubscription({ canAddResources: false, accessState: 'RESTRICTED' });
    expect(hostelAddStatus(s)).toMatchObject({ blocked: true });
    expect(hostelAddStatus(s).message).toMatch(/subscription is not active/);
    expect(bedAddStatus(s, 1).blocked).toBe(true);
  });

  test('no data yet means nothing is blocked', () => {
    expect(hostelAddStatus(undefined).blocked).toBe(false);
    expect(bedAddStatus(undefined, 1).blocked).toBe(false);
  });
});

describe('PlanUsageCard', () => {
  test('shows plan, state and every limit against usage', async () => {
    render(<PlanUsageCard />);

    expect(await screen.findByText('Growth')).toBeInTheDocument();
    expect(screen.getByText('Hostels: 2 / 3')).toBeInTheDocument();
    expect(screen.getByText('Managers: 3 / 5')).toBeInTheDocument();
    expect(screen.getByText('Alpha')).toBeInTheDocument();
    expect(screen.getByText('Beds: 78 / 100')).toBeInTheDocument();
    expect(screen.getByText(/Contact the platform administrator to upgrade your plan/)).toBeInTheDocument();
  });

  test('shows the trial end date and days left during a trial', async () => {
    subscriptionApi.mine.mockResolvedValue(mySubscription({ status: 'TRIAL', accessState: 'TRIAL', trialEndsOn: '2026-11-10', daysRemaining: 14 }));
    render(<PlanUsageCard />);

    expect(await screen.findByText('Trial ends')).toBeInTheDocument();
    expect(screen.getByText('14')).toBeInTheDocument();
  });

  test('renders nothing (and does not break the page) if the subscription cannot be loaded', async () => {
    subscriptionApi.mine.mockRejectedValue(new Error('network'));
    const { container } = render(<PlanUsageCard />);
    await waitFor(() => expect(subscriptionApi.mine).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
});

describe('notices and banner', () => {
  test('the notice lists the server warnings and is danger-styled when a limit is reached', () => {
    const s = mySubscription({ warnings: ['Hostels: 3 / 3. Limit reached; contact the platform administrator to upgrade.'] });
    render(<SubscriptionNotice summary={s} />);
    expect(screen.getByRole('status')).toHaveClass('alert-danger');
    expect(screen.getByRole('status')).toHaveTextContent('Hostels: 3 / 3');
  });

  test('a near-limit warning is only a caution, and the filter narrows which warnings show', () => {
    const s = mySubscription({ warnings: ['Hostels: 3 / 3 used.', 'Manager accounts: 4 / 5 used.'] });
    render(<SubscriptionNotice summary={s} only={(w) => w.startsWith('Hostels')} />);
    expect(screen.getByRole('status')).toHaveClass('alert-warning');
    expect(screen.getByRole('status')).not.toHaveTextContent('Manager accounts');
  });

  test('no warnings, no notice', () => {
    const { container } = render(<SubscriptionNotice summary={mySubscription()} />);
    expect(container).toBeEmptyDOMElement();
  });

  test('the banner stays hidden for a healthy subscription', async () => {
    const { container } = render(<SubscriptionBanner />);
    await waitFor(() => expect(subscriptionApi.mine).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  test('the banner appears during grace and when restricted, but never blocks reading', async () => {
    subscriptionApi.mine.mockResolvedValue(mySubscription({
      accessState: 'GRACE', warnings: ['Your subscription period has ended. You can keep adding resources until 2026-12-08.'],
    }));
    const { unmount } = render(<SubscriptionBanner />);
    expect(await screen.findByRole('status')).toHaveClass('alert-warning');
    unmount();

    subscriptionApi.mine.mockResolvedValue(mySubscription({
      accessState: 'RESTRICTED', canAddResources: false, warnings: ['Your subscription is not active. Your records stay available.'],
    }));
    render(<SubscriptionBanner />);
    const banner = await screen.findByRole('status');
    expect(banner).toHaveClass('alert-danger');
    expect(banner).toHaveTextContent('records stay available');
  });

  test('the banner warns when a trial ends within a week', async () => {
    subscriptionApi.mine.mockResolvedValue(mySubscription({
      status: 'TRIAL', accessState: 'TRIAL', daysRemaining: 3, warnings: ['Your trial ends in 3 day(s).'],
    }));
    render(<SubscriptionBanner />);
    expect(await screen.findByRole('status')).toHaveTextContent('Your trial ends in 3 day(s).');
  });
});

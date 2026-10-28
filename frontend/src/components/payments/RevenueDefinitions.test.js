import { render, screen } from '@testing-library/react';
import RevenueDefinitions from './RevenueDefinitions';

const summary = { monthCollections: 13000, monthNetRevenue: 7500, monthDepositReceipts: 5000, monthAdvanceRefunds: 500, monthDepositRefunds: 3000 };

test('states what Collections and Net revenue mean, with this month\'s components', () => {
  render(<RevenueDefinitions summary={summary} />);
  const text = screen.getByTestId('revenue-definitions').textContent;

  expect(text).toMatch(/Collections = all money received this month \(₹13,000/);
  expect(text).toMatch(/including security deposits and\s+advance payments/);
  expect(text).toMatch(/Refunds are not deducted/);
  expect(text).toMatch(/Net revenue \(₹7,500/);
  expect(text).toMatch(/deposits received \(₹5,000/);
  expect(text).toMatch(/advance credit refunded \(₹500/);
});

test('says why returned deposits are not deducted from Net revenue', () => {
  render(<RevenueDefinitions summary={summary} />);
  const text = screen.getByTestId('revenue-definitions').textContent;
  expect(text).toMatch(/Deposits returned to residents \(₹3,000/);
  expect(text).toMatch(/not deducted, because deposits were never counted as revenue/);
});

test('renders nothing until the summary has loaded', () => {
  const { container } = render(<RevenueDefinitions summary={undefined} />);
  expect(container).toBeEmptyDOMElement();
});

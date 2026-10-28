import { money } from '../../utils/format';

/**
 * Spells out exactly what the two headline money figures mean, with this month's components, so "Collections" and
 * "Net revenue" cannot be mistaken for each other.
 */
export default function RevenueDefinitions({ summary }) {
  if (!summary) return null;
  return (
    <div className="small text-muted-hh mb-4" data-testid="revenue-definitions">
      <p className="mb-1">
        <strong>Collections</strong> = all money received this month ({money(summary.monthCollections)}), including security deposits and
        advance payments. Refunds are not deducted.
      </p>
      <p className="mb-0">
        <strong>Net revenue</strong> ({money(summary.monthNetRevenue)}) = Collections − deposits received ({money(summary.monthDepositReceipts)})
        − advance credit refunded ({money(summary.monthAdvanceRefunds)}). Deposits returned to residents ({money(summary.monthDepositRefunds)})
        are not deducted, because deposits were never counted as revenue.
      </p>
    </div>
  );
}

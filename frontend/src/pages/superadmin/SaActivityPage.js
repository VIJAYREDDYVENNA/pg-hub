import { useState } from 'react';
import useApi from '../../hooks/useApi';
import { adminApi } from '../../services/endpoints';
import PageHeader from '../../components/common/PageHeader';
import DataTable from '../../components/common/DataTable';
import Pagination from '../../components/common/Pagination';
import { EmptyState, ErrorState, LoadingState } from '../../components/common/States';
import { dateTime, label } from '../../utils/format';
import { OwnerLink, saCrumbs } from './saCommon';

export default function SaActivityPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const { data, loading, error, reload } = useApi(() => adminApi.activity({ page, size }), [page, size]);

  const columns = [
    { key: 'createdAt', label: 'When', sortable: false, render: (a) => dateTime(a.createdAt) },
    { key: 'action', label: 'Action', sortable: false, render: (a) => <span className="fw-medium">{label(a.action)}</span> },
    { key: 'actor', label: 'By', sortable: false, hideOnMobile: true, render: (a) => a.actorName || '—' },
    { key: 'owner', label: 'Owner', sortable: false, render: (a) => (a.ownerId ? <OwnerLink id={a.ownerId}>{a.ownerName || `#${a.ownerId}`}</OwnerLink> : '—') },
    { key: 'details', label: 'Details', sortable: false, hideOnMobile: true, render: (a) => <span className="text-muted-hh">{a.details || '—'}</span> },
  ];

  return (
    <>
      <PageHeader title="Activity logs" icon="bi-clock-history" subtitle="Audited actions across all owner accounts"
        breadcrumb={saCrumbs({ label: 'Activity Logs' })} />
      {error ? <ErrorState error={error} onRetry={reload} /> : (
        <div style={{ opacity: loading && data ? 0.6 : 1, transition: 'opacity .15s' }}>
          <DataTable tableId="sa-activity" columns={columns} rows={data?.content || []}
            empty={loading && !data ? <LoadingState /> : <EmptyState icon="bi-clock-history" title="No activity recorded yet" />}
            footer={data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size}
              noun="entries" onChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />} />
        </div>
      )}
    </>
  );
}

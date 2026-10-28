import { Link } from 'react-router-dom';
import Icon from '../components/common/Icon';

export default function NotFoundPage() {
  return (
    <div className="hh-card hh-state py-5">
      <Icon className="bi bi-signpost-split fs-1 text-primary-hh" />
      <h1 className="h4 mt-3">Page not found</h1>
      <p className="text-muted-hh">The page you are looking for doesn't exist or was moved.</p>
      <Link to="/" className="btn btn-primary">Go to dashboard</Link>
    </div>
  );
}

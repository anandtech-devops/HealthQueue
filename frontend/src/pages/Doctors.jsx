import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { EmptyState, ErrorBanner, Spinner } from '../components/Ui';
import { shortTime } from '../utils/dates';

export default function Doctors() {
  const { user } = useAuth();
  const [departments, setDepartments] = useState([]);
  const [departmentId, setDepartmentId] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/api/departments').then((r) => setDepartments(r.data)).catch(() => {});
  }, []);

  const load = useCallback(() => {
    setData(null);
    setError('');
    api.get('/api/doctors', { params: { departmentId: departmentId || undefined, page, size: 9 } })
      .then((r) => setData(r.data))
      .catch((err) => setError(errorMessage(err, 'Could not load doctors.')));
  }, [departmentId, page]);

  useEffect(load, [load]);

  return (
    <div className="container">
      <h1>Doctors</h1>
      <div className="toolbar">
        <div className="field inline">
          <label htmlFor="dept">Department</label>
          <select id="dept" value={departmentId} onChange={(e) => { setPage(0); setDepartmentId(e.target.value); }}>
            <option value="">All departments</option>
            {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </div>
      </div>

      <ErrorBanner message={error} onRetry={load} />
      {!data && !error && <Spinner label="Loading doctors..." />}
      {data && data.content.length === 0 && (
        <EmptyState title="No doctors found">There are no doctors in this department yet.</EmptyState>
      )}
      {data && data.content.length > 0 && (
        <div className="grid">
          {data.content.map((d) => (
            <article key={d.id} className="card doctor-card">
              <h3>{d.fullName}</h3>
              <p className="muted">{d.title} &middot; {d.department}</p>
              <p className="small">Hours {shortTime(d.startTime)}&ndash;{shortTime(d.endTime)} &middot; {d.slotMinutes}-min slots</p>
              <p className="small muted">{d.workingDays.split(',').join(' ')}</p>
              {user?.role === 'PATIENT' || !user ? (
                <Link className="btn btn-primary" to={`/book/${d.id}`}>Book appointment</Link>
              ) : null}
            </article>
          ))}
        </div>
      )}
      {data && data.totalPages > 1 && (
        <div className="pager">
          <button type="button" className="btn btn-small btn-outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <span>Page {page + 1} of {data.totalPages}</span>
          <button type="button" className="btn btn-small btn-outline" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}
    </div>
  );
}

import { useCallback, useEffect, useState } from 'react';
import { api, errorMessage } from '../api/client';
import DoctorQueuePanel from '../components/DoctorQueuePanel';
import { ConfirmDialog, EmptyState, ErrorBanner, Spinner, StatusBadge, SuccessBanner } from '../components/Ui';
import { shortTime, toISODate } from '../utils/dates';

const TABS = ['Overview', 'Queue', 'Appointments', 'Doctors'];
const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

function useDoctors() {
  const [doctors, setDoctors] = useState(null);
  const [error, setError] = useState('');
  const load = useCallback(() => {
    api.get('/api/admin/doctors', { params: { size: 100 } })
      .then((r) => setDoctors(r.data.content))
      .catch((err) => setError(errorMessage(err, 'Could not load doctors.')));
  }, []);
  useEffect(load, [load]);
  return { doctors, error, reload: load };
}

function Overview({ date, setDate }) {
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');
  const load = useCallback(() => {
    api.get('/api/admin/overview', { params: { date } })
      .then((r) => { setRows(r.data); setError(''); })
      .catch((err) => setError(errorMessage(err, 'Could not load the overview.')));
  }, [date]);
  useEffect(() => { setRows(null); load(); const t = setInterval(load, 15000); return () => clearInterval(t); }, [load]);

  const count = (r, s) => r.counts?.[s] || 0;
  return (
    <>
      <div className="toolbar">
        <div className="field inline"><label htmlFor="ov-date">Date</label>
          <input id="ov-date" type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} /></div>
      </div>
      <ErrorBanner message={error} onRetry={load} />
      {!rows && !error && <Spinner label="Loading overview..." />}
      {rows && rows.length === 0 && <EmptyState title="No active doctors">Add a doctor in the Doctors tab.</EmptyState>}
      <div className="grid">
        {rows?.map((r) => (
          <article key={r.doctorId} className="card">
            <h3>{r.doctorName}</h3>
            <p className="muted">{r.title}</p>
            <p>Now serving: <strong>{r.nowServing || '—'}</strong></p>
            <ul className="count-list">
              <li><span>Waiting</span><strong>{count(r, 'BOOKED') + count(r, 'WAITING')}</strong></li>
              <li><span>In progress</span><strong>{count(r, 'CALLED') + count(r, 'IN_CONSULTATION')}</strong></li>
              <li><span>Completed</span><strong>{count(r, 'COMPLETED')}</strong></li>
              <li><span>Cancelled / skipped</span><strong>{count(r, 'CANCELLED') + count(r, 'SKIPPED')}</strong></li>
            </ul>
          </article>
        ))}
      </div>
    </>
  );
}

function QueueTab({ doctors }) {
  const [doctorId, setDoctorId] = useState('');
  const active = (doctors || []).filter((d) => d.active);
  useEffect(() => { if (!doctorId && active.length) setDoctorId(String(active[0].id)); }, [active, doctorId]);
  if (!doctors) return <Spinner />;
  if (active.length === 0) return <EmptyState title="No active doctors" />;
  return (
    <>
      <div className="field inline">
        <label htmlFor="q-doctor">Doctor</label>
        <select id="q-doctor" value={doctorId} onChange={(e) => setDoctorId(e.target.value)}>
          {active.map((d) => <option key={d.id} value={d.id}>{d.fullName} - {d.title}</option>)}
        </select>
      </div>
      {doctorId && <DoctorQueuePanel key={doctorId} doctorId={doctorId} isAdmin />}
    </>
  );
}

function AppointmentsTab({ doctors, date, setDate }) {
  const [doctorId, setDoctorId] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [toCancel, setToCancel] = useState(null);

  const load = useCallback(() => {
    api.get('/api/admin/appointments', { params: { date, doctorId: doctorId || undefined, status: status || undefined, page, size: 20 } })
      .then((r) => { setData(r.data); setError(''); })
      .catch((err) => setError(errorMessage(err, 'Could not load appointments.')));
  }, [date, doctorId, status, page]);
  useEffect(() => { setData(null); load(); }, [load]);

  const act = async (path, success) => {
    try { await api.post(path); setNotice(success); setError(''); load(); } catch (err) { setError(errorMessage(err)); }
  };

  return (
    <>
      <div className="toolbar">
        <div className="field inline"><label htmlFor="ap-date">Date</label>
          <input id="ap-date" type="date" value={date} onChange={(e) => { if (e.target.value) { setPage(0); setDate(e.target.value); } }} /></div>
        <div className="field inline"><label htmlFor="ap-doc">Doctor</label>
          <select id="ap-doc" value={doctorId} onChange={(e) => { setPage(0); setDoctorId(e.target.value); }}>
            <option value="">All</option>
            {(doctors || []).map((d) => <option key={d.id} value={d.id}>{d.fullName}</option>)}
          </select></div>
        <div className="field inline"><label htmlFor="ap-status">Status</label>
          <select id="ap-status" value={status} onChange={(e) => { setPage(0); setStatus(e.target.value); }}>
            <option value="">All</option>
            {['BOOKED', 'WAITING', 'CALLED', 'IN_CONSULTATION', 'COMPLETED', 'CANCELLED', 'SKIPPED'].map((s) => <option key={s} value={s}>{s}</option>)}
          </select></div>
      </div>
      <ErrorBanner message={error} />
      <SuccessBanner message={notice} />
      {!data && !error && <Spinner label="Loading appointments..." />}
      {data && data.content.length === 0 && <EmptyState title="No appointments match these filters" />}
      {data && data.content.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Token</th><th>Doctor</th><th>Patient</th><th>Time</th><th>Status</th><th>Actions</th></tr></thead>
            <tbody>
              {data.content.map((a) => (
                <tr key={a.id}>
                  <td><strong>{a.token}</strong></td><td>{a.doctorName}</td><td>{a.patientName}</td>
                  <td>{shortTime(a.slotTime)}</td><td><StatusBadge status={a.status} /></td>
                  <td className="actions">
                    {a.status === 'BOOKED' && <button type="button" className="btn btn-small btn-outline" onClick={() => act(`/api/appointments/${a.id}/check-in`, `${a.token} checked in.`)}>Check in</button>}
                    {['BOOKED', 'WAITING', 'CALLED'].includes(a.status) && <button type="button" className="btn btn-small btn-outline-danger" onClick={() => setToCancel(a)}>Cancel</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && data.totalPages > 1 && (
        <div className="pager">
          <button type="button" className="btn btn-small btn-outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <span>Page {page + 1} of {data.totalPages}</span>
          <button type="button" className="btn btn-small btn-outline" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}
      <ConfirmDialog open={Boolean(toCancel)} danger title={`Cancel ${toCancel?.token}?`}
        message={`The appointment for ${toCancel?.patientName} will be cancelled.`} confirmLabel="Cancel appointment"
        onCancel={() => setToCancel(null)}
        onConfirm={() => { const a = toCancel; setToCancel(null); act(`/api/appointments/${a.id}/cancel`, `${a.token} cancelled.`); }} />
    </>
  );
}

function DoctorsTab({ doctors, reload, error }) {
  const [departments, setDepartments] = useState([]);
  const [form, setForm] = useState({ fullName: '', email: '', password: '', departmentId: '', title: '', slotMinutes: 15, startTime: '09:00', endTime: '17:00', workingDays: ['MON', 'TUE', 'WED', 'THU', 'FRI'] });
  const [formError, setFormError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => { api.get('/api/departments').then((r) => setDepartments(r.data)); }, []);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const toggleDay = (d) => setForm({ ...form, workingDays: form.workingDays.includes(d) ? form.workingDays.filter((x) => x !== d) : [...form.workingDays, d] });

  const create = async (e) => {
    e.preventDefault();
    setFormError(''); setNotice('');
    if (!form.fullName.trim() || !form.email.trim() || !form.title.trim() || !form.departmentId) { setFormError('Name, e-mail, department and title are required.'); return; }
    if (form.password.length < 8) { setFormError('Initial password must be at least 8 characters.'); return; }
    if (form.workingDays.length === 0) { setFormError('Choose at least one working day.'); return; }
    setBusy(true);
    try {
      await api.post('/api/admin/doctors', { ...form, departmentId: Number(form.departmentId), slotMinutes: Number(form.slotMinutes), workingDays: DAYS.filter((d) => form.workingDays.includes(d)).join(',') });
      setNotice(`Doctor ${form.fullName} created.`);
      setForm({ ...form, fullName: '', email: '', password: '', title: '' });
      reload();
    } catch (err) { setFormError(errorMessage(err)); } finally { setBusy(false); }
  };

  const toggleActive = async (d) => {
    try {
      await api.put(`/api/admin/doctors/${d.id}`, { departmentId: d.departmentId, title: d.title, slotMinutes: d.slotMinutes, startTime: d.startTime, endTime: d.endTime, workingDays: d.workingDays, active: !d.active });
      reload();
    } catch (err) { setFormError(errorMessage(err)); }
  };

  return (
    <>
      <ErrorBanner message={error} />
      <h2>Add a doctor</h2>
      <form className="card form grid-form" onSubmit={create} noValidate>
        <div className="field"><label htmlFor="d-name">Full name</label><input id="d-name" value={form.fullName} onChange={set('fullName')} /></div>
        <div className="field"><label htmlFor="d-email">E-mail (login)</label><input id="d-email" type="email" value={form.email} onChange={set('email')} /></div>
        <div className="field"><label htmlFor="d-pass">Initial password</label><input id="d-pass" type="password" autoComplete="new-password" value={form.password} onChange={set('password')} /></div>
        <div className="field"><label htmlFor="d-dept">Department</label>
          <select id="d-dept" value={form.departmentId} onChange={set('departmentId')}><option value="">Select...</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></div>
        <div className="field"><label htmlFor="d-title">Title / specialty</label><input id="d-title" value={form.title} onChange={set('title')} placeholder="General Physician" /></div>
        <div className="field"><label htmlFor="d-slot">Slot length (minutes)</label><input id="d-slot" type="number" min="5" max="120" value={form.slotMinutes} onChange={set('slotMinutes')} /></div>
        <div className="field"><label htmlFor="d-start">Starts</label><input id="d-start" type="time" value={form.startTime} onChange={set('startTime')} /></div>
        <div className="field"><label htmlFor="d-end">Ends</label><input id="d-end" type="time" value={form.endTime} onChange={set('endTime')} /></div>
        <fieldset className="field span-all"><legend>Working days</legend>
          <div className="day-row">{DAYS.map((d) => <label key={d} className="chip"><input type="checkbox" checked={form.workingDays.includes(d)} onChange={() => toggleDay(d)} /> {d}</label>)}</div></fieldset>
        <div className="span-all"><ErrorBanner message={formError} /><SuccessBanner message={notice} />
          <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Saving...' : 'Create doctor'}</button></div>
      </form>

      <h2>All doctors</h2>
      {!doctors && <Spinner />}
      {doctors && (
        <div className="table-wrap"><table className="table">
          <thead><tr><th>Name</th><th>Department</th><th>Hours</th><th>Token</th><th>Status</th><th /></tr></thead>
          <tbody>{doctors.map((d) => (
            <tr key={d.id}><td>{d.fullName}<div className="muted small">{d.title}</div></td><td>{d.department}</td>
              <td>{shortTime(d.startTime)}&ndash;{shortTime(d.endTime)}<div className="muted small">{d.workingDays.split(',').join(' ')}</div></td>
              <td>{d.tokenPrefix}-###</td><td>{d.active ? 'Active' : 'Inactive'}</td>
              <td><button type="button" className="btn btn-small btn-outline" onClick={() => toggleActive(d)}>{d.active ? 'Deactivate' : 'Activate'}</button></td></tr>
          ))}</tbody>
        </table></div>
      )}
    </>
  );
}

export default function AdminDashboard() {
  const [tab, setTab] = useState('Overview');
  const [date, setDate] = useState(toISODate());
  const { doctors, error, reload } = useDoctors();

  return (
    <div className="container">
      <h1>Reception / Admin</h1>
      <div className="tabs" role="tablist" aria-label="Admin sections">
        {TABS.map((t) => (
          <button key={t} type="button" role="tab" aria-selected={tab === t} className={`tab ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>{t}</button>
        ))}
      </div>
      {tab === 'Overview' && <Overview date={date} setDate={setDate} />}
      {tab === 'Queue' && <QueueTab doctors={doctors} />}
      {tab === 'Appointments' && <AppointmentsTab doctors={doctors} date={date} setDate={setDate} />}
      {tab === 'Doctors' && <DoctorsTab doctors={doctors} reload={reload} error={error} />}
    </div>
  );
}

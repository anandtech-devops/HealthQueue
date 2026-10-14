import { useEffect, useState } from 'react';
import { api, errorMessage } from '../api/client';
import DoctorQueuePanel from '../components/DoctorQueuePanel';
import { ErrorBanner, Spinner } from '../components/Ui';

export default function DoctorDashboard() {
  const [doctor, setDoctor] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/api/doctor/me').then((r) => setDoctor(r.data)).catch((err) => setError(errorMessage(err, 'No doctor profile found for this account.')));
  }, []);

  return (
    <div className="container">
      <h1>Doctor dashboard</h1>
      <ErrorBanner message={error} />
      {!doctor && !error && <Spinner label="Loading your profile..." />}
      {doctor && (
        <>
          <p className="muted">{doctor.fullName} &middot; {doctor.title} &middot; tokens start with <strong>{doctor.tokenPrefix}</strong></p>
          <DoctorQueuePanel doctorId={doctor.id} />
        </>
      )}
    </div>
  );
}

import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import BookingForm from '../components/BookingForm';
import { ErrorBanner, Spinner } from '../components/Ui';

export default function Book() {
  const { doctorId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [doctor, setDoctor] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get(`/api/doctors/${doctorId}`)
      .then((r) => setDoctor(r.data))
      .catch((err) => setError(errorMessage(err, 'Doctor not found.')));
  }, [doctorId]);

  if (error) return <div className="container"><ErrorBanner message={error} /><Link to="/doctors">Back to doctors</Link></div>;
  if (!doctor) return <div className="container"><Spinner label="Loading doctor..." /></div>;

  return (
    <div className="container narrow">
      <h1>Book an appointment</h1>
      <div className="card doctor-summary">
        <h3>{doctor.fullName}</h3>
        <p className="muted">{doctor.title} &middot; {doctor.department}</p>
      </div>
      {user?.role !== 'PATIENT' ? (
        <div className="alert alert-info">Only patient accounts can book appointments. Please log in as a patient.</div>
      ) : (
        <BookingForm doctor={doctor} onBooked={(appt) => navigate(`/queue/${appt.id}`, { state: { justBooked: true } })} />
      )}
    </div>
  );
}

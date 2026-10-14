import { Link } from 'react-router-dom';
import { homePathFor, useAuth } from '../auth/AuthContext';

export default function Home() {
  const { user } = useAuth();
  return (
    <>
      <section className="hero">
        <div className="container hero-inner">
          <div>
            <h1>Book your visit. Skip the waiting-room guesswork.</h1>
            <p className="lead">
              HealthQueue lets you reserve an appointment, get a queue token instantly and follow your
              position in line live - from your phone, wherever you are.
            </p>
            <div className="hero-actions">
              {user ? (
                <Link to={homePathFor(user)} className="btn btn-primary btn-large">Go to my dashboard</Link>
              ) : (
                <>
                  <Link to="/register" className="btn btn-primary btn-large">Create an account</Link>
                  <Link to="/login" className="btn btn-outline btn-large">Log in</Link>
                </>
              )}
              <Link to="/doctors" className="btn btn-green btn-large">Find a doctor</Link>
            </div>
          </div>
          <div className="hero-card" aria-hidden="true">
            <div className="queue-label">Example</div>
            <div className="hero-token">A-012</div>
            <div className="hero-row"><span>Now serving</span><strong>A-008</strong></div>
            <div className="hero-row"><span>Patients ahead</span><strong>3</strong></div>
            <div className="hero-row"><span>Estimated wait</span><strong>~15 min</strong></div>
          </div>
        </div>
      </section>

      <section className="container features">
        <div className="card"><h3>1. Choose a doctor</h3><p>Browse departments and see who is available.</p></div>
        <div className="card"><h3>2. Pick a time</h3><p>Select a date and a free slot. Your token is issued immediately.</p></div>
        <div className="card"><h3>3. Watch the queue</h3><p>See who is being served and roughly how long you will wait.</p></div>
      </section>

      <p className="container muted small center">
        HealthQueue manages appointments and queues only. It does not provide medical advice, diagnosis or treatment.
        In an emergency, contact your local emergency number.
      </p>
    </>
  );
}

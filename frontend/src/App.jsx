import { Route, Routes } from 'react-router-dom';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
import AdminDashboard from './pages/AdminDashboard';
import Book from './pages/Book';
import Dashboard from './pages/Dashboard';
import DoctorDashboard from './pages/DoctorDashboard';
import Doctors from './pages/Doctors';
import Home from './pages/Home';
import Login from './pages/Login';
import NotFound from './pages/NotFound';
import QueueTracking from './pages/QueueTracking';
import Register from './pages/Register';

export default function App() {
  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>
      <Navbar />
      <main id="main">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/register" element={<Register />} />
          <Route path="/login" element={<Login />} />
          <Route path="/doctors" element={<Doctors />} />
          <Route path="/book/:doctorId" element={<ProtectedRoute roles={['PATIENT']}><Book /></ProtectedRoute>} />
          <Route path="/dashboard" element={<ProtectedRoute roles={['PATIENT']}><Dashboard /></ProtectedRoute>} />
          <Route path="/queue/:appointmentId" element={<ProtectedRoute roles={['PATIENT']}><QueueTracking /></ProtectedRoute>} />
          <Route path="/doctor" element={<ProtectedRoute roles={['DOCTOR']}><DoctorDashboard /></ProtectedRoute>} />
          <Route path="/admin" element={<ProtectedRoute roles={['ADMIN']}><AdminDashboard /></ProtectedRoute>} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
      <footer className="footer">
        <div className="container">HealthQueue &middot; Appointment and queue management only - not medical advice.</div>
      </footer>
    </>
  );
}

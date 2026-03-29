import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import PrivateRoute from './components/PrivateRoute';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import EquipmentPage from './pages/EquipmentPage';
import WorkOrderPage from './pages/WorkOrderPage';
import AlarmPage from './pages/AlarmPage';
import DefectPage from './pages/DefectPage';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/*" element={
            <PrivateRoute>
              <Layout>
                <Routes>
                  <Route path="/"            element={<DashboardPage />} />
                  <Route path="/equipment"   element={<EquipmentPage />} />
                  <Route path="/work-orders" element={<WorkOrderPage />} />
                  <Route path="/alarms"      element={<AlarmPage />} />
                  <Route path="/defects"     element={<DefectPage />} />
                  <Route path="*"            element={<Navigate to="/" replace />} />
                </Routes>
              </Layout>
            </PrivateRoute>
          } />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

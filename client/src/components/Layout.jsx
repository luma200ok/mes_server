import { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const NAV_ITEMS = [
  { to: '/',            label: '대시보드' },
  { to: '/equipment',   label: '설비 관리' },
  { to: '/work-orders', label: '작업지시' },
  { to: '/defects',     label: '불량 관리' },
  { to: '/alarms',      label: '알람 이력' },
];

const ADMIN_NAV_ITEMS = [
  { to: '/users', label: '사용자 관리' },
];

function useIsMobile() {
  const [isMobile, setIsMobile] = useState(() => window.innerWidth < 768);
  useEffect(() => {
    const handler = () => setIsMobile(window.innerWidth < 768);
    window.addEventListener('resize', handler);
    return () => window.removeEventListener('resize', handler);
  }, []);
  return isMobile;
}

export default function Layout({ children }) {
  const { logout, isAdmin } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const isMobile = useIsMobile();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const isActive = (to) => to === '/' ? pathname === '/' : pathname.startsWith(to);

  const closeSidebar = () => setSidebarOpen(false);

  return (
    <div style={styles.root}>
      {/* 모바일 오버레이 */}
      {isMobile && sidebarOpen && (
        <div style={styles.overlay} onClick={closeSidebar} />
      )}

      {/* 사이드바 */}
      <aside style={{
        ...styles.sidebar,
        ...(isMobile ? {
          position: 'fixed',
          left: sidebarOpen ? 0 : '-220px',
          top: 0,
          height: '100vh',
          zIndex: 1000,
          transition: 'left 0.25s ease',
          boxShadow: sidebarOpen ? '4px 0 20px rgba(0,0,0,0.4)' : 'none',
        } : {}),
      }}>
        <h2 style={styles.logo}>MES</h2>
        <nav style={styles.nav}>
          {NAV_ITEMS.map(({ to, label }) => (
            <Link
              key={to}
              style={{ ...styles.link, ...(isActive(to) ? styles.linkActive : {}) }}
              to={to}
              onClick={isMobile ? closeSidebar : undefined}
            >
              {label}
            </Link>
          ))}
          {isAdmin && (
            <>
              <div style={styles.divider} />
              {ADMIN_NAV_ITEMS.map(({ to, label }) => (
                <Link
                  key={to}
                  style={{ ...styles.link, ...(isActive(to) ? styles.linkActive : {}) }}
                  to={to}
                  onClick={isMobile ? closeSidebar : undefined}
                >
                  {label}
                </Link>
              ))}
            </>
          )}
        </nav>
        <button style={styles.logout} onClick={handleLogout}>로그아웃</button>
      </aside>

      {/* 메인 영역 */}
      <div style={{ ...styles.mainWrapper, marginLeft: isMobile ? 0 : '200px' }}>
        {/* 모바일 상단 헤더 */}
        {isMobile && (
          <header style={styles.mobileHeader}>
            <button
              style={styles.hamburger}
              onClick={() => setSidebarOpen(v => !v)}
              aria-label="메뉴 열기"
            >
              {sidebarOpen ? '✕' : '☰'}
            </button>
            <span style={styles.headerTitle}>MES</span>
          </header>
        )}
        <main style={{ ...styles.main, padding: isMobile ? '16px 12px' : '24px' }}>
          {children}
        </main>
      </div>
    </div>
  );
}

const styles = {
  root:        { display: 'flex', height: '100vh', fontFamily: 'sans-serif' },
  overlay:     { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 999 },
  sidebar:     { width: '200px', background: '#001529', display: 'flex', flexDirection: 'column', padding: '24px 16px', flexShrink: 0 },
  logo:        { color: '#fff', marginBottom: '32px', fontSize: '20px' },
  nav:         { display: 'flex', flexDirection: 'column', gap: '8px', flex: 1 },
  link:        { color: '#ccc', textDecoration: 'none', padding: '8px 12px', borderRadius: '4px', fontSize: '14px' },
  linkActive:  { color: '#fff', background: '#1890ff' },
  mainWrapper: { flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' },
  mobileHeader:{ background: '#001529', padding: '0 16px', height: '48px', display: 'flex', alignItems: 'center', gap: '12px', flexShrink: 0 },
  hamburger:   { background: 'transparent', border: 'none', color: '#fff', fontSize: '20px', cursor: 'pointer', padding: '4px 8px', lineHeight: 1 },
  headerTitle: { color: '#fff', fontWeight: 700, fontSize: '16px' },
  main:        { flex: 1, background: '#f0f2f5', overflowY: 'auto' },
  logout:      { color: '#ccc', background: 'transparent', border: '1px solid #ccc', borderRadius: '4px', padding: '8px', cursor: 'pointer', fontSize: '13px' },
  divider:     { borderTop: '1px solid #2a3f5f', margin: '8px 0' },
};

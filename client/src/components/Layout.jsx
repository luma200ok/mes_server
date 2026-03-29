import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const NAV_ITEMS = [
  { to: '/',           label: '대시보드' },
  { to: '/equipment',  label: '설비 관리' },
  { to: '/work-orders',label: '작업지시' },
  { to: '/defects',    label: '불량 관리' },
  { to: '/alarms',     label: '알람 이력' },
];

export default function Layout({ children }) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const isActive = (to) => to === '/' ? pathname === '/' : pathname.startsWith(to);

  return (
    <div style={styles.root}>
      <aside style={styles.sidebar}>
        <h2 style={styles.logo}>MES</h2>
        <nav style={styles.nav}>
          {NAV_ITEMS.map(({ to, label }) => (
            <Link
              key={to}
              style={{ ...styles.link, ...(isActive(to) ? styles.linkActive : {}) }}
              to={to}
            >
              {label}
            </Link>
          ))}
        </nav>
        <button style={styles.logout} onClick={handleLogout}>로그아웃</button>
      </aside>
      <main style={styles.main}>{children}</main>
    </div>
  );
}

const styles = {
  root:    { display:'flex', height:'100vh', fontFamily:'sans-serif' },
  sidebar: { width:'200px', background:'#001529', display:'flex', flexDirection:'column', padding:'24px 16px' },
  logo:    { color:'#fff', marginBottom:'32px', fontSize:'20px' },
  nav:     { display:'flex', flexDirection:'column', gap:'8px', flex:1 },
  link:      { color:'#ccc', textDecoration:'none', padding:'8px 12px', borderRadius:'4px', fontSize:'14px' },
  linkActive: { color:'#fff', background:'#1890ff' },
  main:    { flex:1, background:'#f0f2f5', padding:'24px', overflowY:'auto' },
  logout:  { color:'#ccc', background:'transparent', border:'1px solid #ccc', borderRadius:'4px', padding:'8px', cursor:'pointer', fontSize:'13px' },
};

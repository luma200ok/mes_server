import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'

const root = createRoot(document.getElementById('root'));

const renderApp = () =>
  root.render(<StrictMode><App /></StrictMode>);

const renderLoading = (msg = '서버 연결 중...', color = '#888') =>
  root.render(
    <div style={{ display:'flex', flexDirection:'column', alignItems:'center', justifyContent:'center', height:'100vh', background:'#f0f2f5', gap:16 }}>
      <div style={{ width:40, height:40, border:'4px solid #1890ff', borderTopColor:'transparent', borderRadius:'50%', animation:'spin 0.8s linear infinite' }} />
      <p style={{ color, fontSize:14 }}>{msg}</p>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );

renderLoading();

const TIMEOUT_MS = 10_000;
const INTERVAL_MS = 800;
const start = Date.now();

const check = async () => {
  try {
    const res = await fetch('/actuator/health', { cache: 'no-store' });
    if (res.ok) { renderApp(); return; }
  } catch { /* 아직 미응답 */ }

  if (Date.now() - start >= TIMEOUT_MS) {
    renderLoading('서버 응답 없음 — 페이지를 새로고침 해주세요.', '#e74c3c');
    return;
  }

  setTimeout(check, INTERVAL_MS);
};

check();

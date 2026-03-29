import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { login } from '../api/auth';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError]       = useState('');
  const { saveToken } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const res = await login(username, password);
      saveToken(res.data.token);
      navigate('/');
    } catch {
      setError('아이디 또는 비밀번호가 올바르지 않습니다.');
    }
  };

  return (
    <div style={styles.wrapper}>
      <form style={styles.box} onSubmit={handleSubmit}>
        <h2 style={{ marginBottom: 24 }}>MES 로그인</h2>
        {error && <p style={styles.error}>{error}</p>}
        <input style={styles.input} placeholder="아이디" value={username}
          onChange={e => setUsername(e.target.value)} />
        <input style={styles.input} type="password" placeholder="비밀번호" value={password}
          onChange={e => setPassword(e.target.value)} />
        <button style={styles.btn} type="submit">로그인</button>
        <p style={styles.link}>계정이 없으신가요? <Link to="/register">회원가입</Link></p>
      </form>
    </div>
  );
}

const styles = {
  wrapper: { display:'flex', justifyContent:'center', alignItems:'center', height:'100vh', background:'#f0f2f5' },
  box:     { background:'#fff', padding:'40px', borderRadius:'8px', width:'320px', boxShadow:'0 2px 12px rgba(0,0,0,0.1)', display:'flex', flexDirection:'column', gap:'12px' },
  input:   { padding:'10px', borderRadius:'4px', border:'1px solid #d9d9d9', fontSize:'14px' },
  btn:     { padding:'10px', background:'#1890ff', color:'#fff', border:'none', borderRadius:'4px', cursor:'pointer', fontSize:'14px' },
  error:   { color:'red', fontSize:'13px', margin:0 },
  link:    { textAlign:'center', fontSize:'13px', color:'#666' },
};

import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { register } from '../api/auth';

export default function RegisterPage() {
  const [form, setForm]   = useState({ username: '', password: '', role: 'USER' });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const navigate = useNavigate();

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await register(form.username, form.password, form.role);
      setSuccess(true);
      setTimeout(() => navigate('/login'), 1500);
    } catch (err) {
      const msg = err.response?.data?.message || '회원가입에 실패했습니다.';
      setError(msg);
    }
  };

  return (
    <div style={styles.wrapper}>
      <form style={styles.box} onSubmit={handleSubmit}>
        <h2 style={{ marginBottom: 24 }}>MES 회원가입</h2>
        {error   && <p style={styles.error}>{error}</p>}
        {success && <p style={styles.success}>가입 완료! 로그인 페이지로 이동합니다.</p>}
        <input
          style={styles.input} name="username" placeholder="아이디 (4자 이상)"
          value={form.username} onChange={handleChange} minLength={4} required
        />
        <input
          style={styles.input} name="password" type="password" placeholder="비밀번호 (6자 이상)"
          value={form.password} onChange={handleChange} minLength={6} required
        />
        <select style={styles.input} name="role" value={form.role} onChange={handleChange}>
          <option value="USER">일반 사용자</option>
          <option value="ADMIN">관리자</option>
        </select>
        <button style={styles.btn} type="submit">가입하기</button>
        <p style={styles.link}>이미 계정이 있으신가요? <Link to="/login">로그인</Link></p>
      </form>
    </div>
  );
}

const styles = {
  wrapper:  { display:'flex', justifyContent:'center', alignItems:'center', height:'100vh', background:'#f0f2f5' },
  box:      { background:'#fff', padding:'40px', borderRadius:'8px', width:'320px', boxShadow:'0 2px 12px rgba(0,0,0,0.1)', display:'flex', flexDirection:'column', gap:'12px' },
  input:    { padding:'10px', borderRadius:'4px', border:'1px solid #d9d9d9', fontSize:'14px' },
  btn:      { padding:'10px', background:'#1890ff', color:'#fff', border:'none', borderRadius:'4px', cursor:'pointer', fontSize:'14px' },
  error:    { color:'red', fontSize:'13px', margin:0 },
  success:  { color:'green', fontSize:'13px', margin:0 },
  link:     { textAlign:'center', fontSize:'13px', color:'#666' },
};

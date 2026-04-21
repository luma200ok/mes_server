import { useEffect, useState } from 'react';
import { getUsers, updateRole } from '../api/user';

export default function UserManagePage() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchUsers = async () => {
    try {
      const res = await getUsers();
      setUsers(res.data);
    } catch {
      setError('사용자 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchUsers(); }, []);

  const handleRoleChange = async (userId, currentRole) => {
    const newRole = currentRole === 'ADMIN' ? 'OPERATOR' : 'ADMIN';
    if (!confirm(`권한을 ${newRole}으로 변경하시겠습니까?`)) return;
    try {
      await updateRole(userId, newRole);
      setUsers(prev => prev.map(u => u.id === userId ? { ...u, role: newRole } : u));
    } catch {
      alert('권한 변경에 실패했습니다.');
    }
  };

  if (loading) return <p>불러오는 중...</p>;
  if (error) return <p style={{ color: 'red' }}>{error}</p>;

  return (
    <div>
      <h2 style={styles.title}>사용자 관리</h2>
      <div className="table-scroll">
      <table style={styles.table}>
        <thead>
          <tr style={styles.thead}>
            <th style={styles.th}>ID</th>
            <th style={styles.th}>아이디</th>
            <th style={styles.th}>권한</th>
            <th style={styles.th}>가입일</th>
            <th style={styles.th}>권한 변경</th>
          </tr>
        </thead>
        <tbody>
          {users.map(user => (
            <tr key={user.id} style={styles.tr}>
              <td style={styles.td}>{user.id}</td>
              <td style={styles.td}>{user.username}</td>
              <td style={styles.td}>
                <span style={{ ...styles.badge, ...(user.role === 'ADMIN' ? styles.badgeAdmin : styles.badgeOp) }}>
                  {user.role}
                </span>
              </td>
              <td style={styles.td}>{new Date(user.createdAt).toLocaleDateString()}</td>
              <td style={styles.td}>
                <button
                  style={{ ...styles.btn, ...(user.role === 'ADMIN' ? styles.btnDown : styles.btnUp) }}
                  onClick={() => handleRoleChange(user.id, user.role)}
                >
                  {user.role === 'ADMIN' ? 'OPERATOR로 변경' : 'ADMIN으로 승급'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      </div>
    </div>
  );
}

const styles = {
  title: { marginBottom: 20 },
  table: { width: '100%', borderCollapse: 'collapse', background: '#fff', borderRadius: 8, overflow: 'hidden', boxShadow: '0 1px 4px rgba(0,0,0,0.1)' },
  thead: { background: '#fafafa' },
  th: { padding: '12px 16px', textAlign: 'left', fontWeight: 600, fontSize: 14, borderBottom: '1px solid #f0f0f0' },
  tr: { borderBottom: '1px solid #f0f0f0' },
  td: { padding: '12px 16px', fontSize: 14 },
  badge: { padding: '2px 10px', borderRadius: 12, fontSize: 12, fontWeight: 600 },
  badgeAdmin: { background: '#fff1f0', color: '#cf1322' },
  badgeOp: { background: '#f0f5ff', color: '#2f54eb' },
  btn: { padding: '4px 12px', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 13 },
  btnUp: { background: '#1890ff', color: '#fff' },
  btnDown: { background: '#ff4d4f', color: '#fff' },
};

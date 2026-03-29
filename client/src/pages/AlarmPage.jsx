import { useState, useEffect } from 'react';
import { getAlarmsByEquipment, getAlarmsByPeriod } from '../api/alarm';
import { getEquipments } from '../api/equipment';

const TABS = ['설비별 조회', '기간별 조회'];

export default function AlarmPage() {
  const [tab, setTab]             = useState(0);
  const [equipmentId, setEquipId] = useState('');
  const [equipments, setEquipments] = useState([]);
  const [from, setFrom]           = useState('');
  const [to, setTo]               = useState('');
  const [alarms, setAlarms]       = useState([]);
  const [err, setErr]             = useState('');

  useEffect(() => {
    getEquipments().then(r => setEquipments(r.data)).catch(() => {});
  }, []);

  const loadByEquipment = async () => {
    if (!equipmentId) return;
    setErr('');
    try {
      const res = await getAlarmsByEquipment(equipmentId);
      setAlarms(res.data.content);
    } catch {
      setErr('조회 실패');
    }
  };

  const loadByPeriod = async () => {
    if (!from || !to) { setErr('시작/종료 일시를 입력하세요.'); return; }
    setErr('');
    try {
      const res = await getAlarmsByPeriod(from, to);
      setAlarms(res.data.content);
    } catch {
      setErr('조회 실패');
    }
  };

  return (
    <div>
      <h2 style={{ margin: '0 0 16px' }}>알람 이력</h2>

      {/* 탭 */}
      <div style={styles.tabRow}>
        {TABS.map((t, i) => (
          <button key={t} style={{ ...styles.tab, ...(tab === i ? styles.tabActive : {}) }}
            onClick={() => { setTab(i); setAlarms([]); setErr(''); }}>
            {t}
          </button>
        ))}
      </div>

      {/* 설비별 검색 */}
      {tab === 0 && (
        <div style={styles.filterRow}>
          <input
            style={styles.input}
            placeholder="설비 ID (예: EQ-001)"
            value={equipmentId}
            onChange={e => setEquipId(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && loadByEquipment()}
            list="equipment-list"
          />
          <datalist id="equipment-list">
            {equipments.map(eq => (
              <option key={eq.equipmentId} value={eq.equipmentId}>{eq.name}</option>
            ))}
          </datalist>
          <button style={styles.btn} onClick={loadByEquipment}>조회</button>
        </div>
      )}

      {/* 기간별 검색 */}
      {tab === 1 && (
        <div style={styles.filterRow}>
          <input style={styles.input} type="datetime-local" value={from} onChange={e => setFrom(e.target.value)} />
          <span style={{ fontSize: '13px' }}>~</span>
          <input style={styles.input} type="datetime-local" value={to} onChange={e => setTo(e.target.value)} />
          <button style={styles.btn} onClick={loadByPeriod}>조회</button>
        </div>
      )}

      {err && <p style={{ color: 'red', fontSize: '13px', marginBottom: '8px' }}>{err}</p>}

      <table style={styles.table}>
        <thead>
          <tr>{['설비', '항목', '현재값', '임계값', 'Discord 전송', '발생 시각'].map(h => (
            <th key={h} style={styles.th}>{h}</th>
          ))}</tr>
        </thead>
        <tbody>
          {alarms.length === 0 ? (
            <tr><td colSpan={6} style={{ ...styles.td, textAlign: 'center', color: '#999' }}>데이터 없음</td></tr>
          ) : alarms.map(a => (
            <tr key={a.id}>
              <td style={styles.td}>{a.equipmentId}</td>
              <td style={styles.td}>{a.metric}</td>
              <td style={styles.td}>{a.currentValue?.toFixed(2)}</td>
              <td style={styles.td}>{a.threshold?.toFixed(2)}</td>
              <td style={styles.td}>{a.discordSent ? '✅' : '❌'}</td>
              <td style={styles.td}>{new Date(a.sentAt).toLocaleString('ko-KR')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const styles = {
  tabRow:    { display: 'flex', gap: '4px', marginBottom: '16px' },
  tab:       { padding: '8px 20px', border: '1px solid #d9d9d9', borderRadius: '4px', background: '#fff', cursor: 'pointer', fontSize: '13px', color: '#555' },
  tabActive: { background: '#1890ff', color: '#fff', borderColor: '#1890ff' },
  filterRow: { display: 'flex', gap: '8px', alignItems: 'center', marginBottom: '12px' },
  input:     { padding: '8px', border: '1px solid #d9d9d9', borderRadius: '4px', fontSize: '13px' },
  btn:       { padding: '8px 16px', background: '#1890ff', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '13px' },
  table:     { width: '100%', borderCollapse: 'collapse', background: '#fff', borderRadius: '8px', overflow: 'hidden' },
  th:        { background: '#fafafa', padding: '12px', textAlign: 'left', fontSize: '13px', borderBottom: '1px solid #f0f0f0' },
  td:        { padding: '12px', fontSize: '13px', borderBottom: '1px solid #f0f0f0' },
};

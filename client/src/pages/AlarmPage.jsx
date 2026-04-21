import { useState, useEffect } from 'react';
import { getAlarmsByEquipment, getAlarmsByPeriod } from '../api/alarm';
import { getEquipments } from '../api/equipment';

const TABS = ['설비별 조회', '기간별 조회'];

export default function AlarmPage() {
  const [tab, setTab]               = useState(0);
  const [equipmentId, setEquipId]   = useState('');
  const [equipments, setEquipments] = useState([]);
  const [from, setFrom]             = useState('');
  const [to, setTo]                 = useState('');
  const [alarms, setAlarms]         = useState([]);
  const [err, setErr]               = useState('');
  const [loading, setLoading]       = useState(false);

  // 설비 목록 로드 + 첫 번째 설비 자동 선택
  useEffect(() => {
    getEquipments()
      .then(r => {
        setEquipments(r.data);
        if (r.data.length > 0) setEquipId(r.data[0].equipmentId);
      })
      .catch(() => {});
  }, []);

  // 설비별 탭: 설비 선택 변경 시 자동 조회
  useEffect(() => {
    if (tab !== 0 || !equipmentId) return;
    let cancelled = false;
    setErr('');
    setLoading(true);
    getAlarmsByEquipment(equipmentId)
      .then(res => { if (!cancelled) setAlarms(res.data.content); })
      .catch(() => { if (!cancelled) setErr('조회 실패'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [tab, equipmentId]);


  const loadByPeriod = async () => {
    if (!from || !to) { setErr('시작/종료 일시를 입력하세요.'); return; }
    setErr('');
    setLoading(true);
    try {
      const res = await getAlarmsByPeriod(from, to);
      setAlarms(res.data.content);
    } catch {
      setErr('조회 실패');
    } finally {
      setLoading(false);
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

      {/* 설비별 검색 — 드롭다운 */}
      {tab === 0 && (
        <div style={styles.filterRow}>
          <select
            style={styles.select}
            value={equipmentId}
            onChange={e => setEquipId(e.target.value)}
          >
            {equipments.map(eq => (
              <option key={eq.equipmentId} value={eq.equipmentId}>
                {eq.equipmentId} — {eq.name}
              </option>
            ))}
          </select>
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
      {loading && <p style={{ fontSize: '13px', color: '#888', marginBottom: '8px' }}>로딩 중...</p>}

      <div className="table-scroll">
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
              <td style={{ ...styles.td, color: '#e74c3c', fontWeight: 600 }}>{a.currentValue?.toFixed(2)}</td>
              <td style={styles.td}>{a.threshold?.toFixed(2)}</td>
              <td style={styles.td}>{a.discordSent ? '✅' : '❌'}</td>
              <td style={styles.td}>{a.sentAt ? new Date(a.sentAt).toLocaleString('ko-KR') : '-'}</td>
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
  select:    { padding: '8px 12px', border: '1px solid #d9d9d9', borderRadius: '4px', fontSize: '13px', minWidth: '220px', cursor: 'pointer' },
  input:     { padding: '8px', border: '1px solid #d9d9d9', borderRadius: '4px', fontSize: '13px' },
  btn:       { padding: '8px 16px', background: '#1890ff', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '13px' },
  table:     { width: '100%', borderCollapse: 'collapse', background: '#fff', borderRadius: '8px', overflow: 'hidden', boxShadow: '0 1px 4px rgba(0,0,0,0.08)' },
  th:        { background: '#fafafa', padding: '12px', textAlign: 'left', fontSize: '13px', borderBottom: '1px solid #f0f0f0' },
  td:        { padding: '12px', fontSize: '13px', borderBottom: '1px solid #f0f0f0' },
};

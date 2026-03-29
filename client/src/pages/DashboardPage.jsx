import { useEffect, useState } from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { getOee, getSensorHistory, exportExcel, exportCsv } from '../api/dashboard';

const todayStr  = () => new Date().toISOString().slice(0, 10);
const todayStart = () => { const d = new Date(); d.setHours(0,0,0,0); return d.toISOString().slice(0,16); };
const nowStr    = () => new Date().toISOString().slice(0, 16);

const EQUIP_IDS = ['EQ-001', 'EQ-002', 'EQ-003'];

export default function DashboardPage() {
  const [sensorLogs, setSensorLogs]       = useState([]);
  const [latestByEquip, setLatestByEquip] = useState({});

  // OEE
  const [oeeEquip, setOeeEquip] = useState('EQ-001');
  const [oeeDate,  setOeeDate]  = useState(todayStr());
  const [oeeData,  setOeeData]  = useState(null);
  const [oeeErr,   setOeeErr]   = useState('');

  // 센서 이력
  const [histEquip, setHistEquip] = useState('EQ-001');
  const [histFrom,  setHistFrom]  = useState(todayStart());
  const [histTo,    setHistTo]    = useState(nowStr());
  const [histData,  setHistData]  = useState([]);
  const [histErr,   setHistErr]   = useState('');

  // 내보내기
  const [expEquip, setExpEquip] = useState('EQ-001');
  const [expFrom,  setExpFrom]  = useState(todayStart());
  const [expTo,    setExpTo]    = useState(nowStr());

  useEffect(() => {
    let es = null;
    let retryTimer = null;
    let retryDelay = 3000;

    const connect = () => {
      es = new EventSource('/api/sse/subscribe');
      es.onopen = () => { retryDelay = 3000; };
      es.addEventListener('sensorData', (e) => {
        try {
          const data = JSON.parse(e.data);
          const time = new Date().toLocaleTimeString('ko-KR');
          setSensorLogs(prev => [...prev.slice(-59), { time, ...data }]);
          setLatestByEquip(prev => ({ ...prev, [data.equipmentId]: { ...data, updatedAt: Date.now() } }));
        } catch { /* 무시 */ }
      });
      es.onerror = () => {
        es.close();
        retryTimer = setTimeout(() => {
          retryDelay = Math.min(retryDelay * 2, 15000);
          connect();
        }, retryDelay);
      };
    };

    connect();
    return () => {
      es?.close();
      clearTimeout(retryTimer);
    };
  }, []);

  const handleOee = async () => {
    setOeeErr(''); setOeeData(null);
    if (!oeeEquip || !oeeDate) { setOeeErr('설비 ID와 날짜를 입력해주세요.'); return; }
    try {
      const res = await getOee(oeeEquip, oeeDate);
      setOeeData(res.data);
    } catch (err) {
      setOeeErr(err.response?.data?.message ?? 'OEE 조회 실패');
    }
  };

  const handleSensorHistory = async () => {
    setHistErr(''); setHistData([]);
    if (!histEquip) { setHistErr('설비 ID를 입력해주세요.'); return; }
    try {
      const res = await getSensorHistory(histEquip, histFrom, histTo);
      setHistData(res.data);
    } catch (err) {
      setHistErr(err.response?.data?.message ?? '센서 이력 조회 실패');
    }
  };

  const handleExport = async (type) => {
    if (!expEquip) { alert('설비 ID를 입력해주세요.'); return; }
    try {
      const res = type === 'excel'
        ? await exportExcel(expEquip, expFrom, expTo)
        : await exportCsv(expEquip, expFrom, expTo);
      const ext = type === 'excel' ? 'xlsx' : 'csv';
      const url = URL.createObjectURL(new Blob([res.data]));
      const a   = document.createElement('a');
      a.href = url; a.download = `sensor_${expEquip}.${ext}`; a.click();
      URL.revokeObjectURL(url);
    } catch { alert('내보내기 실패'); }
  };

  // 온도 추이: 설비별 색상
  const equipColors = { 'EQ-001': '#1890ff', 'EQ-002': '#52c41a', 'EQ-003': '#fa8c16' };

  return (
    <div>
      <h2 style={{ margin: '0 0 20px' }}>실시간 대시보드</h2>

      {/* 설비별 최신 상태 카드 */}
      <div style={S.cardRow}>
        {EQUIP_IDS.map(id => {
          const d = latestByEquip[id];
          const alive = d && (Date.now() - d.updatedAt) < 10000;
          return (
            <div key={id} style={{ ...S.card, borderTop: `4px solid ${alive ? (d.status === 'FAULT' ? '#ff4d4f' : '#52c41a') : '#d9d9d9'}` }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <h3 style={{ margin: 0, fontSize: 15 }}>{id}</h3>
                <span style={{ ...S.badge, background: alive ? (d.status === 'FAULT' ? '#ff4d4f' : '#52c41a') : '#bfbfbf' }}>
                  {alive ? d.status : 'OFFLINE'}
                </span>
              </div>
              {d ? (
                <>
                  <p style={S.stat}>온도 <b>{d.temperature?.toFixed(1)}°C</b></p>
                  <p style={S.stat}>진동 <b>{d.vibration?.toFixed(2)}</b></p>
                  <p style={S.stat}>RPM <b>{d.rpm?.toFixed(0)}</b></p>
                </>
              ) : (
                <p style={{ color: '#bbb', fontSize: 12, margin: '8px 0 0' }}>데이터 없음</p>
              )}
            </div>
          );
        })}
      </div>

      {/* 온도 추이 차트 (SSE 실시간) */}
      <div style={S.section}>
        <h3 style={S.sectionTitle}>실시간 온도 추이</h3>
        {sensorLogs.length === 0
          ? <p style={{ color: '#999', fontSize: 13 }}>시뮬레이터를 실행하면 실시간 데이터가 표시됩니다.</p>
          : (
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={sensorLogs}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="time" tick={{ fontSize: 10 }} interval="preserveStartEnd" />
                <YAxis domain={['auto', 'auto']} />
                <Tooltip />
                <Legend />
                <Line type="monotone" dataKey="temperature" stroke="#1890ff" dot={false} name="온도(°C)" isAnimationActive={false} />
              </LineChart>
            </ResponsiveContainer>
          )
        }
      </div>

      {/* OEE 통계 */}
      <div style={S.section}>
        <h3 style={S.sectionTitle}>OEE 통계</h3>
        <div style={S.filterRow}>
          <select style={S.select} value={oeeEquip} onChange={e => setOeeEquip(e.target.value)}>
            {EQUIP_IDS.map(id => <option key={id} value={id}>{id}</option>)}
          </select>
          <input style={S.input} type="date" value={oeeDate} onChange={e => setOeeDate(e.target.value)} />
          <button style={S.btn} onClick={handleOee}>조회</button>
        </div>
        {oeeErr && <p style={S.err}>{oeeErr}</p>}
        {oeeData && (
          <>
            <div style={S.oeeRow}>
              {[
                { label: 'OEE',  value: `${(oeeData.oee * 100).toFixed(1)}%`,          color: '#1890ff' },
                { label: '가용률', value: `${(oeeData.availability * 100).toFixed(1)}%`, color: '#52c41a' },
                { label: '성능률', value: `${(oeeData.performance * 100).toFixed(1)}%`,  color: '#fa8c16' },
                { label: '품질률', value: `${(oeeData.quality * 100).toFixed(1)}%`,      color: '#722ed1' },
              ].map(({ label, value, color }) => (
                <div key={label} style={S.oeeCard}>
                  <div style={S.oeeLabel}>{label}</div>
                  <div style={{ ...S.oeeValue, color }}>{value}</div>
                </div>
              ))}
            </div>
            <div style={{ ...S.filterRow, marginTop: 12, fontSize: 12, color: '#555', gap: 16 }}>
              <span>총 작업지시 <b>{oeeData.totalWorkOrders}</b></span>
              <span>완료 <b>{oeeData.completedWorkOrders}</b></span>
              <span>불량 <b>{oeeData.defectiveWorkOrders}</b></span>
              <span>계획수량 <b>{oeeData.totalPlannedQty}</b></span>
              <span>완료수량 <b>{oeeData.totalCompletedQty}</b></span>
              <span>불량수량 <b>{oeeData.totalDefectQty}</b></span>
            </div>
          </>
        )}
      </div>

      {/* 센서 이력 조회 */}
      <div style={S.section}>
        <h3 style={S.sectionTitle}>센서 이력 조회</h3>
        <div style={S.filterRow}>
          <select style={S.select} value={histEquip} onChange={e => setHistEquip(e.target.value)}>
            {EQUIP_IDS.map(id => <option key={id} value={id}>{id}</option>)}
          </select>
          <input style={S.input} type="datetime-local" value={histFrom} onChange={e => setHistFrom(e.target.value)} />
          <span style={{ fontSize: 13 }}>~</span>
          <input style={S.input} type="datetime-local" value={histTo} onChange={e => setHistTo(e.target.value)} />
          <button style={S.btn} onClick={handleSensorHistory}>조회</button>
        </div>
        {histErr && <p style={S.err}>{histErr}</p>}
        {histData.length > 0 ? (
          <ResponsiveContainer width="100%" height={220} style={{ marginTop: 12 }}>
            <LineChart data={histData.map(h => ({ ...h, time: new Date(h.recordedAt).toLocaleTimeString('ko-KR') }))}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="time" tick={{ fontSize: 10 }} interval="preserveStartEnd" />
              <YAxis />
              <Tooltip />
              <Legend />
              <Line type="monotone" dataKey="avgTemperature" stroke="#1890ff" dot={false} name="평균온도" />
              <Line type="monotone" dataKey="avgVibration"   stroke="#52c41a" dot={false} name="평균진동" />
              <Line type="monotone" dataKey="avgRpm"         stroke="#faad14" dot={false} name="평균RPM" />
            </LineChart>
          </ResponsiveContainer>
        ) : (
          !histErr && <p style={{ color: '#999', fontSize: 13, marginTop: 8 }}>조회 결과가 없습니다. (데이터는 1분 주기로 집계됩니다)</p>
        )}
      </div>

      {/* Excel / CSV 내보내기 */}
      <div style={S.section}>
        <h3 style={S.sectionTitle}>데이터 내보내기</h3>
        <div style={S.filterRow}>
          <select style={S.select} value={expEquip} onChange={e => setExpEquip(e.target.value)}>
            {EQUIP_IDS.map(id => <option key={id} value={id}>{id}</option>)}
          </select>
          <input style={S.input} type="datetime-local" value={expFrom} onChange={e => setExpFrom(e.target.value)} />
          <span style={{ fontSize: 13 }}>~</span>
          <input style={S.input} type="datetime-local" value={expTo} onChange={e => setExpTo(e.target.value)} />
          <button style={{ ...S.btn, background: '#52c41a' }} onClick={() => handleExport('excel')}>Excel</button>
          <button style={{ ...S.btn, background: '#faad14' }} onClick={() => handleExport('csv')}>CSV</button>
        </div>
      </div>
    </div>
  );
}

const S = {
  cardRow:      { display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 24 },
  card:         { background: '#fff', borderRadius: 8, padding: 16, minWidth: 160, flex: '1 1 150px', maxWidth: 200, boxShadow: '0 1px 4px rgba(0,0,0,0.1)' },
  stat:         { margin: '4px 0', fontSize: 13, color: '#555', display: 'flex', justifyContent: 'space-between' },
  badge:        { color: '#fff', borderRadius: 4, padding: '2px 8px', fontSize: 11 },
  section:      { background: '#fff', borderRadius: 8, padding: 20, boxShadow: '0 1px 4px rgba(0,0,0,0.08)', marginBottom: 20 },
  sectionTitle: { margin: '0 0 12px', fontSize: 15, fontWeight: 600 },
  filterRow:    { display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' },
  select:       { padding: '7px 8px', border: '1px solid #d9d9d9', borderRadius: 4, fontSize: 13, background: '#fff', color: '#000' },
  input:        { padding: '7px 8px', border: '1px solid #d9d9d9', borderRadius: 4, fontSize: 13 },
  btn:          { padding: '7px 16px', background: '#1890ff', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 13 },
  err:          { color: 'red', fontSize: 12, marginTop: 6 },
  oeeRow:       { display: 'flex', gap: 12, marginTop: 12, flexWrap: 'wrap' },
  oeeCard:      { background: '#f0f2f5', borderRadius: 8, padding: '12px 20px', textAlign: 'center', minWidth: 90 },
  oeeLabel:     { fontSize: 12, color: '#888', marginBottom: 4 },
  oeeValue:     { fontSize: 22, fontWeight: 'bold' },
};

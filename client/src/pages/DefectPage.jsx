import { useEffect, useState } from 'react';
import { getDefects, getDefectsByWorkOrder, getDefectSummary, createDefect } from '../api/defect';
import { getWorkOrders } from '../api/workorder';

function useIsMobile() {
  const [m, setM] = useState(() => window.innerWidth < 768);
  useEffect(() => {
    const h = () => setM(window.innerWidth < 768);
    window.addEventListener('resize', h);
    return () => window.removeEventListener('resize', h);
  }, []);
  return m;
}

const DEFECT_TYPES  = ['DIMENSION', 'SURFACE', 'ASSEMBLY', 'OTHER'];
const SENSOR_TYPES  = ['TEMPERATURE', 'VIBRATION', 'RPM'];
const ALL_TYPES     = [...DEFECT_TYPES, ...SENSOR_TYPES];

const DEFECT_LABELS = {
  DIMENSION: '치수', SURFACE: '표면', ASSEMBLY: '조립', OTHER: '기타',
  TEMPERATURE: '온도', VIBRATION: '진동', RPM: 'RPM',
};
const DEFECT_COLORS = {
  DIMENSION: '#ff4d4f', SURFACE: '#faad14', ASSEMBLY: '#1890ff', OTHER: '#8c8c8c',
  TEMPERATURE: '#ff7a45', VIBRATION: '#722ed1', RPM: '#13c2c2',
};

const EMPTY_BY_TYPE = Object.fromEntries(ALL_TYPES.map(t => [t, 0]));

const empty = { workOrderId: '', defectType: 'DIMENSION', qty: 1, note: '' };

export default function DefectPage() {
  const isMobile = useIsMobile();
  const [workOrders, setWorkOrders] = useState([]);
  const [selectedWo, setSelectedWo] = useState('');
  const [list, setList]             = useState([]);
  const [summary, setSummary]       = useState({ total: 0, byType: { ...EMPTY_BY_TYPE } });
  const [form, setForm]             = useState(empty);
  const [showForm, setShowForm]     = useState(false);
  const [error, setError]           = useState('');

  useEffect(() => {
    let timer;
    const tryLoad = () => {
      Promise.all([
        getWorkOrders().then(r => setWorkOrders(r.data)),
        loadDefects(null),
        loadSummary(null),
      ]).catch(() => { timer = setTimeout(tryLoad, 3000); });
    };
    tryLoad();
    return () => clearTimeout(timer);
  }, []);

  const loadDefects = (woId) => {
    if (woId) {
      return getDefectsByWorkOrder(woId)
        .then(r => setList(r.data))
        .catch(() => setList([]));
    }
    return getDefects({ size: 100 })
      .then(r => setList(r.data.content))
      .catch(() => setList([]));
  };

  const loadSummary = (woId) => {
    const params = {};
    return getDefectSummary(params)
      .then(r => {
        const s = r.data;
        const byType = { ...EMPTY_BY_TYPE };
        if (s.qtyByType) {
          Object.entries(s.qtyByType).forEach(([type, qty]) => {
            if (byType[type] !== undefined) byType[type] = qty;
          });
        }
        setSummary({ total: s.totalQty ?? 0, byType });
      })
      .catch(() => {});
  };

  const handleWoChange = (e) => {
    const id = e.target.value;
    setSelectedWo(id);
    setForm(f => ({ ...f, workOrderId: id }));
    loadDefects(id || null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await createDefect({ ...form, workOrderId: Number(form.workOrderId), qty: Number(form.qty) });
      setForm(empty);
      setShowForm(false);
      loadDefects(selectedWo || null);
      loadSummary(null);
    } catch (err) {
      setError(err.response?.data?.message ?? '등록 실패. 다시 시도해주세요.');
    }
  };

  return (
    <div>
      <div style={styles.header}>
        <h2 style={{ margin: 0 }}>불량 관리</h2>
        <button style={styles.addBtn} onClick={() => setShowForm(v => !v)}>
          {showForm ? '취소' : '+ 불량 등록'}
        </button>
      </div>

      {/* 요약 카드 */}
      {isMobile ? (
        /* 모바일: 2열 그리드 */
        <>
          <div style={styles.cardGrid}>
            <div style={{ ...styles.summaryCard, gridColumn: 'span 2' }}>
              <div style={styles.summaryValue}>{summary.total}</div>
              <div style={styles.summaryLabel}>총 불량 수량</div>
            </div>
            {DEFECT_TYPES.map(t => (
              <div key={t} style={{ ...styles.summaryCard, borderTop: `3px solid ${DEFECT_COLORS[t]}` }}>
                <div style={{ ...styles.summaryValue, color: DEFECT_COLORS[t] }}>{summary.byType[t]}</div>
                <div style={styles.summaryLabel}>{DEFECT_LABELS[t]}</div>
              </div>
            ))}
          </div>
          <div style={styles.sectionHeading}>센서 자동 감지</div>
          <div style={{ ...styles.cardGrid, marginBottom: '16px' }}>
            {SENSOR_TYPES.map(t => (
              <div key={t} style={{ ...styles.summaryCard, borderTop: `3px solid ${DEFECT_COLORS[t]}` }}>
                <div style={{ ...styles.summaryValue, color: DEFECT_COLORS[t] }}>{summary.byType[t]}</div>
                <div style={styles.summaryLabel}>{DEFECT_LABELS[t]}</div>
              </div>
            ))}
          </div>
        </>
      ) : (
        /* 데스크탑: 기존 가로 행 */
        <>
          <div style={styles.summaryRow}>
            <div style={styles.summaryCard}>
              <div style={styles.summaryValue}>{summary.total}</div>
              <div style={styles.summaryLabel}>총 불량 수량</div>
            </div>
            {DEFECT_TYPES.map(t => (
              <div key={t} style={{ ...styles.summaryCard, borderTop: `3px solid ${DEFECT_COLORS[t]}` }}>
                <div style={{ ...styles.summaryValue, color: DEFECT_COLORS[t] }}>{summary.byType[t]}</div>
                <div style={styles.summaryLabel}>{DEFECT_LABELS[t]}</div>
              </div>
            ))}
          </div>
          <div style={{ ...styles.summaryRow, marginTop: '-8px' }}>
            <div style={styles.sectionLabel}>센서 자동 감지</div>
            {SENSOR_TYPES.map(t => (
              <div key={t} style={{ ...styles.summaryCard, borderTop: `3px solid ${DEFECT_COLORS[t]}` }}>
                <div style={{ ...styles.summaryValue, color: DEFECT_COLORS[t] }}>{summary.byType[t]}</div>
                <div style={styles.summaryLabel}>{DEFECT_LABELS[t]}</div>
              </div>
            ))}
          </div>
        </>
      )}

      {/* 등록 폼 */}
      {showForm && (
        <form onSubmit={handleSubmit} style={styles.form}>
          <div style={styles.row}>
            <label style={styles.label}>작업지시</label>
            <select
              style={styles.input}
              value={form.workOrderId}
              onChange={e => { setForm(f => ({ ...f, workOrderId: e.target.value })); setSelectedWo(e.target.value); loadDefects(e.target.value); }}
              required
            >
              <option value="">선택</option>
              {workOrders.map(wo => (
                <option key={wo.id} value={wo.id}>{wo.workOrderNo} | {wo.equipmentId}</option>
              ))}
            </select>
          </div>
          <div style={styles.row}>
            <label style={styles.label}>불량 유형</label>
            <select style={styles.input} value={form.defectType} onChange={e => setForm(f => ({ ...f, defectType: e.target.value }))}>
              {DEFECT_TYPES.map(t => <option key={t} value={t}>{DEFECT_LABELS[t]}</option>)}
            </select>
          </div>
          <div style={styles.row}>
            <label style={styles.label}>수량</label>
            <input style={styles.input} type="number" min={1} value={form.qty} onChange={e => setForm(f => ({ ...f, qty: e.target.value }))} required />
          </div>
          <div style={styles.row}>
            <label style={styles.label}>비고</label>
            <input style={styles.input} type="text" value={form.note} onChange={e => setForm(f => ({ ...f, note: e.target.value }))} />
          </div>
          {error && <p style={{ color: 'red', fontSize: '13px', margin: 0 }}>{error}</p>}
          <button type="submit" style={styles.submitBtn}>등록</button>
        </form>
      )}

      {/* 필터 */}
      <div style={styles.filterRow}>
        <label style={{ marginRight: '8px', fontSize: '14px' }}>작업지시 필터:</label>
        <select style={{ ...styles.input, width: '300px' }} value={selectedWo} onChange={handleWoChange}>
          <option value="">전체</option>
          {workOrders.map(wo => (
            <option key={wo.id} value={wo.id}>{wo.workOrderNo} | {wo.equipmentId} | {statusLabel(wo.status)}</option>
          ))}
        </select>
      </div>

      {/* 목록 */}
      <div className="table-scroll">
      <table style={styles.table}>
        <thead>
          <tr>
            {['작업지시 번호', '설비', '불량 유형', '수량', '발생일시', '비고'].map(h => (
              <th key={h} style={styles.th}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {list.length === 0 ? (
            <tr><td colSpan={6} style={{ ...styles.td, textAlign: 'center', color: '#aaa' }}>데이터 없음</td></tr>
          ) : list.map(d => (
            <tr key={d.id}>
              <td style={styles.td}>{d.workOrderNo}</td>
              <td style={styles.td}>{d.equipmentId}</td>
              <td style={styles.td}>
                <span style={{ ...styles.badge, background: DEFECT_COLORS[d.defectType] || '#8c8c8c' }}>
                  {DEFECT_LABELS[d.defectType] ?? d.defectType}
                </span>
              </td>
              <td style={styles.td}>{d.qty}</td>
              <td style={styles.td}>{new Date(d.detectedAt).toLocaleString('ko-KR')}</td>
              <td style={{ ...styles.td, color: '#888' }}>{d.note ?? '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
      </div>
    </div>
  );
}

const statusLabel = (s) => ({ PENDING: '대기', IN_PROGRESS: '진행중', COMPLETED: '완료', DEFECTIVE: '불량' }[s] || s);

const styles = {
  header:       { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' },
  addBtn:       { background: '#1890ff', color: '#fff', border: 'none', borderRadius: '4px', padding: '8px 16px', cursor: 'pointer', fontSize: '13px' },
  summaryRow:   { display: 'flex', gap: '12px', marginBottom: '20px', flexWrap: 'wrap' },
  summaryCard:  { background: '#fff', borderRadius: '8px', padding: '16px 24px', minWidth: '110px', textAlign: 'center', boxShadow: '0 1px 4px rgba(0,0,0,0.08)', borderTop: '3px solid #d9d9d9' },
  summaryValue: { fontSize: '24px', fontWeight: 700, color: '#262626' },
  summaryLabel: { fontSize: '12px', color: '#8c8c8c', marginTop: '4px' },
  form:         { background: '#fff', borderRadius: '8px', padding: '20px', marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '12px', maxWidth: '480px' },
  row:          { display: 'flex', alignItems: 'center', gap: '12px' },
  label:        { width: '80px', fontSize: '13px', flexShrink: 0 },
  input:        { flex: 1, padding: '6px 8px', border: '1px solid #d9d9d9', borderRadius: '4px', fontSize: '13px' },
  submitBtn:    { background: '#52c41a', color: '#fff', border: 'none', borderRadius: '4px', padding: '8px 20px', cursor: 'pointer', fontSize: '13px', alignSelf: 'flex-start' },
  sectionLabel:   { display: 'flex', alignItems: 'center', fontSize: '12px', color: '#8c8c8c', minWidth: '80px', fontWeight: 600 },
  cardGrid:       { display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '10px', marginBottom: '10px' },
  sectionHeading: { fontSize: '12px', color: '#8c8c8c', fontWeight: 600, marginBottom: '8px', marginTop: '4px' },
  filterRow:    { marginBottom: '12px', display: 'flex', alignItems: 'center' },
  table:        { width: '100%', borderCollapse: 'collapse', background: '#fff', borderRadius: '8px', overflow: 'hidden' },
  th:           { background: '#fafafa', padding: '12px', textAlign: 'left', fontSize: '13px', borderBottom: '1px solid #f0f0f0' },
  td:           { padding: '12px', fontSize: '13px', borderBottom: '1px solid #f0f0f0' },
  badge:        { color: '#fff', padding: '2px 8px', borderRadius: '4px', fontSize: '12px' },
};

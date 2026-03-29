import { useEffect, useState } from 'react';
import { getDefects, createDefect } from '../api/defect';
import { getWorkOrders } from '../api/workorder';

const DEFECT_TYPES = ['DIMENSION', 'SURFACE', 'ASSEMBLY', 'OTHER'];
const DEFECT_LABELS = { DIMENSION: '치수', SURFACE: '표면', ASSEMBLY: '조립', OTHER: '기타' };

const empty = { workOrderId: '', defectType: 'DIMENSION', qty: 1, note: '' };

export default function DefectPage() {
  const [workOrders, setWorkOrders] = useState([]);
  const [selectedWo, setSelectedWo] = useState('');
  const [list, setList] = useState([]);
  const [form, setForm] = useState(empty);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let timer;
    const tryLoad = () => {
      Promise.all([
        getWorkOrders().then(r => setWorkOrders(r.data)),
        getDefects(null).then(r => setList(r.data)),
      ]).catch(() => { timer = setTimeout(tryLoad, 3000); });
    };
    tryLoad();
    return () => clearTimeout(timer);
  }, []);

  const loadDefects = (woId) => {
    // woId가 없으면 전체 조회
    getDefects(woId || null).then(r => setList(r.data)).catch(() => setList([]));
  };

  const handleWoChange = (e) => {
    const id = e.target.value;
    setSelectedWo(id);
    setForm(f => ({ ...f, workOrderId: id }));
    loadDefects(id);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await createDefect({ ...form, workOrderId: Number(form.workOrderId), qty: Number(form.qty) });
      setForm(empty);
      setShowForm(false);
      loadDefects(selectedWo);
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
                <option key={wo.id} value={wo.id}>{wo.workOrderNo} ({wo.equipmentId})</option>
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

      <div style={styles.filterRow}>
        <label style={{ marginRight: '8px', fontSize: '14px' }}>작업지시로 조회:</label>
        <select style={{ ...styles.input, width: '280px' }} value={selectedWo} onChange={handleWoChange}>
          <option value="">전체 선택</option>
          {workOrders.map(wo => (
            <option key={wo.id} value={wo.id}>{wo.workOrderNo} ({wo.equipmentId})</option>
          ))}
        </select>
      </div>

      <table style={styles.table}>
        <thead>
          <tr>
            {['작업지시', '설비', '불량 유형', '수량', '발생일시', '비고'].map(h => (
              <th key={h} style={styles.th}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {list.length === 0 ? (
            <tr><td colSpan={6} style={{ ...styles.td, textAlign: 'center', color: '#aaa' }}>데이터 없음</td></tr>
          ) : list.map(d => (
            <tr key={d.id}>
              <td style={styles.td}>{d.workOrderId}</td>
              <td style={styles.td}>{d.equipmentId}</td>
              <td style={styles.td}><span style={styles.badge}>{DEFECT_LABELS[d.defectType] ?? d.defectType}</span></td>
              <td style={styles.td}>{d.qty}</td>
              <td style={styles.td}>{new Date(d.detectedAt).toLocaleString('ko-KR')}</td>
              <td style={styles.td}>{d.note ?? '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const styles = {
  header:    { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' },
  addBtn:    { background: '#1890ff', color: '#fff', border: 'none', borderRadius: '4px', padding: '8px 16px', cursor: 'pointer', fontSize: '13px' },
  form:      { background: '#fff', borderRadius: '8px', padding: '20px', marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '12px', maxWidth: '480px' },
  row:       { display: 'flex', alignItems: 'center', gap: '12px' },
  label:     { width: '80px', fontSize: '13px', flexShrink: 0 },
  input:     { flex: 1, padding: '6px 8px', border: '1px solid #d9d9d9', borderRadius: '4px', fontSize: '13px' },
  submitBtn: { background: '#52c41a', color: '#fff', border: 'none', borderRadius: '4px', padding: '8px 20px', cursor: 'pointer', fontSize: '13px', alignSelf: 'flex-start' },
  filterRow: { marginBottom: '12px', display: 'flex', alignItems: 'center' },
  table:     { width: '100%', borderCollapse: 'collapse', background: '#fff', borderRadius: '8px', overflow: 'hidden' },
  th:        { background: '#fafafa', padding: '12px', textAlign: 'left', fontSize: '13px', borderBottom: '1px solid #f0f0f0' },
  td:        { padding: '12px', fontSize: '13px', borderBottom: '1px solid #f0f0f0' },
  badge:     { background: '#ff4d4f', color: '#fff', padding: '2px 8px', borderRadius: '4px', fontSize: '12px' },
};

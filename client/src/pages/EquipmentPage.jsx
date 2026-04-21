import { useEffect, useState } from 'react';
import { getEquipments, createEquipment, deleteEquipment, getEquipmentConfig, saveEquipmentConfig } from '../api/equipment';

const emptyForm = { equipmentId: '', name: '', location: '' };
const emptyConfig = { maxTemperature: '', maxVibration: '', maxRpm: '' };

export default function EquipmentPage() {
  const [list, setList]           = useState([]);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm]           = useState(emptyForm);
  const [createErr, setCreateErr] = useState('');

  const [configTarget, setConfigTarget] = useState(null); // equipmentId
  const [config, setConfig]             = useState(emptyConfig);
  const [configErr, setConfigErr]       = useState('');

  const load = () => getEquipments().then(r => setList(r.data)).catch(() => {});

  useEffect(() => {
    let timer;
    const tryLoad = () => {
      getEquipments()
        .then(r => setList(r.data))
        .catch(() => { timer = setTimeout(tryLoad, 3000); });
    };
    tryLoad();
    return () => clearTimeout(timer);
  }, []);

  // 설비 등록
  const handleCreate = async (e) => {
    e.preventDefault();
    setCreateErr('');
    try {
      await createEquipment(form);
      setForm(emptyForm);
      setShowCreate(false);
      load();
    } catch (err) {
      setCreateErr(err.response?.data?.message ?? '등록 실패');
    }
  };

  // 설비 삭제
  const handleDelete = async (equipmentId) => {
    if (!confirm('삭제하시겠습니까?')) return;
    try {
      await deleteEquipment(equipmentId);
      load();
    } catch (err) {
      alert(err.response?.data?.message ?? '삭제 실패');
    }
  };

  // 설정 패널 열기 (기존 config 로드)
  const openConfig = async (equipmentId) => {
    setConfigTarget(equipmentId);
    setConfigErr('');
    try {
      const res = await getEquipmentConfig(equipmentId);
      const c = res.data;
      setConfig({ maxTemperature: c.maxTemperature, maxVibration: c.maxVibration, maxRpm: c.maxRpm });
    } catch {
      setConfig(emptyConfig);
    }
  };

  // 설정 저장
  const handleSaveConfig = async (e) => {
    e.preventDefault();
    setConfigErr('');
    try {
      await saveEquipmentConfig({
        equipmentId: configTarget,
        maxTemperature: Number(config.maxTemperature),
        maxVibration:   Number(config.maxVibration),
        maxRpm:         Number(config.maxRpm),
      });
      setConfigTarget(null);
    } catch (err) {
      setConfigErr(err.response?.data?.message ?? '저장 실패');
    }
  };

  return (
    <div>
      <div style={styles.header}>
        <h2 style={{ margin: 0 }}>설비 관리</h2>
        <button style={styles.addBtn} onClick={() => { setShowCreate(v => !v); setCreateErr(''); }}>
          {showCreate ? '취소' : '+ 설비 등록'}
        </button>
      </div>

      {/* 등록 폼 */}
      {showCreate && (
        <form onSubmit={handleCreate} style={styles.form}>
          <div style={styles.row}>
            <label style={styles.label}>설비 ID</label>
            <input style={styles.input} placeholder="EQ-004" value={form.equipmentId}
              onChange={e => setForm(f => ({ ...f, equipmentId: e.target.value }))} required />
            <span style={styles.hint}>EQ-000 형식</span>
          </div>
          <div style={styles.row}>
            <label style={styles.label}>이름</label>
            <input style={styles.input} placeholder="CNC 가공기 4호" value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))} required />
          </div>
          <div style={styles.row}>
            <label style={styles.label}>위치</label>
            <input style={styles.input} placeholder="B동 1층" value={form.location}
              onChange={e => setForm(f => ({ ...f, location: e.target.value }))} required />
          </div>
          {createErr && <p style={styles.err}>{createErr}</p>}
          <button type="submit" style={styles.submitBtn}>등록</button>
        </form>
      )}

      {/* 설비 목록 */}
      <div className="table-scroll">
      <table style={styles.table}>
        <thead>
          <tr>{['설비 ID', '이름', '위치', '상태', '액션'].map(h => <th key={h} style={styles.th}>{h}</th>)}</tr>
        </thead>
        <tbody>
          {list.map(eq => (
            <tr key={eq.id}>
              <td style={styles.td}>{eq.equipmentId}</td>
              <td style={styles.td}>{eq.name}</td>
              <td style={styles.td}>{eq.location}</td>
              <td style={styles.td}>
                <span style={{ ...styles.badge, background: statusColor(eq.status) }}>{eq.status}</span>
              </td>
              <td style={styles.td}>
                <button style={styles.configBtn} onClick={() => openConfig(eq.equipmentId)}>설정</button>
                <button style={styles.delBtn} onClick={() => handleDelete(eq.equipmentId)}>삭제</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      </div>

      {/* 설정 패널 (모달) */}
      {configTarget && (
        <div style={styles.overlay}>
          <form style={styles.modal} onSubmit={handleSaveConfig}>
            <h3 style={{ margin: '0 0 16px' }}>{configTarget} 임계값 설정</h3>
            <div style={styles.row}>
              <label style={styles.label}>최대 온도(°C)</label>
              <input style={styles.input} type="number" step="0.1" value={config.maxTemperature}
                onChange={e => setConfig(c => ({ ...c, maxTemperature: e.target.value }))} required />
            </div>
            <div style={styles.row}>
              <label style={styles.label}>최대 진동</label>
              <input style={styles.input} type="number" step="0.01" value={config.maxVibration}
                onChange={e => setConfig(c => ({ ...c, maxVibration: e.target.value }))} required />
            </div>
            <div style={styles.row}>
              <label style={styles.label}>최대 RPM</label>
              <input style={styles.input} type="number" step="1" value={config.maxRpm}
                onChange={e => setConfig(c => ({ ...c, maxRpm: e.target.value }))} required />
            </div>
            {configErr && <p style={styles.err}>{configErr}</p>}
            <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
              <button type="submit" style={styles.submitBtn}>저장</button>
              <button type="button" style={styles.cancelBtn} onClick={() => setConfigTarget(null)}>취소</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}

const statusColor = (s) => ({ RUNNING: '#52c41a', STOPPED: '#faad14', FAULT: 'red' }[s] || '#ccc');

const styles = {
  header:    { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' },
  addBtn:    { background: '#1890ff', color: '#fff', border: 'none', borderRadius: '4px', padding: '8px 16px', cursor: 'pointer', fontSize: '13px' },
  form:      { background: '#fff', borderRadius: '8px', padding: '20px', marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px', maxWidth: '480px' },
  row:       { display: 'flex', alignItems: 'center', gap: '8px' },
  label:     { width: '80px', fontSize: '13px', flexShrink: 0 },
  hint:      { fontSize: '11px', color: '#aaa' },
  input:     { flex: 1, padding: '6px 8px', border: '1px solid #d9d9d9', borderRadius: '4px', fontSize: '13px' },
  submitBtn: { background: '#52c41a', color: '#fff', border: 'none', borderRadius: '4px', padding: '7px 20px', cursor: 'pointer', fontSize: '13px', alignSelf: 'flex-start' },
  cancelBtn: { background: '#fff', color: '#555', border: '1px solid #d9d9d9', borderRadius: '4px', padding: '7px 20px', cursor: 'pointer', fontSize: '13px' },
  err:       { color: 'red', fontSize: '12px', margin: 0 },
  table:     { width: '100%', borderCollapse: 'collapse', background: '#fff', borderRadius: '8px', overflow: 'hidden' },
  th:        { background: '#fafafa', padding: '12px', textAlign: 'left', fontSize: '13px', borderBottom: '1px solid #f0f0f0' },
  td:        { padding: '12px', fontSize: '13px', borderBottom: '1px solid #f0f0f0' },
  badge:     { color: '#fff', padding: '2px 8px', borderRadius: '4px', fontSize: '12px' },
  configBtn: { background: '#1890ff', color: '#fff', border: 'none', borderRadius: '4px', padding: '4px 10px', cursor: 'pointer', fontSize: '12px', marginRight: '6px' },
  delBtn:    { background: '#ff4d4f', color: '#fff', border: 'none', borderRadius: '4px', padding: '4px 10px', cursor: 'pointer', fontSize: '12px' },
  overlay:   { position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', background: 'rgba(0,0,0,0.45)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 },
  modal:     { background: '#fff', borderRadius: '8px', padding: '28px', width: '380px', display: 'flex', flexDirection: 'column', gap: '12px' },
};

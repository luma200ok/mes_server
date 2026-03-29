import { useEffect, useState } from 'react';
import { getWorkOrders, createWorkOrder, changeStatus, uploadExcel, downloadTemplate } from '../api/workorder';

export default function WorkOrderPage() {
  const [list, setList]           = useState([]);
  const [equipmentId, setEquipId] = useState('');
  const [plannedQty, setQty]      = useState('');
  const [uploadResult, setResult] = useState(null);

  const load = () => getWorkOrders().then(r => setList(r.data)).catch(() => {});

  useEffect(() => {
    let timer;
    const tryLoad = () => {
      getWorkOrders()
        .then(r => setList(r.data))
        .catch(() => { timer = setTimeout(tryLoad, 3000); });
    };
    tryLoad();
    return () => clearTimeout(timer);
  }, []);

  // 30초마다 자동 갱신 (수량 카운팅 실시간 반영)
  useEffect(() => {
    const interval = setInterval(load, 30000);
    return () => clearInterval(interval);
  }, []);

  const handleCreate = async (e) => {
    e.preventDefault();
    await createWorkOrder({ equipmentId, plannedQty: Number(plannedQty) });
    setEquipId(''); setQty('');
    load();
  };

  const handleStatus = async (id, status) => {
    try {
      await changeStatus(id, { status });
      load();
    } catch (err) {
      alert(err.response?.data?.message ?? '상태 변경 실패');
    }
  };

  const handleUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const res = await uploadExcel(file);
    setResult(res.data);
    load();
  };

  const handleTemplate = async () => {
    const res = await downloadTemplate();
    const url = URL.createObjectURL(new Blob([res.data]));
    const a = document.createElement('a'); a.href = url; a.download = 'work_order_template.xlsx'; a.click();
  };

  return (
    <div>
      <h2>작업지시 관리</h2>

      {/* 등록 폼 */}
      <form onSubmit={handleCreate} style={styles.form}>
        <input style={styles.input} placeholder="설비 ID (예: EQ-001)" value={equipmentId} onChange={e => setEquipId(e.target.value)} required />
        <input style={styles.input} type="number" placeholder="계획 수량" value={plannedQty} onChange={e => setQty(e.target.value)} min={1} required />
        <button style={styles.btn} type="submit">등록</button>
      </form>

      {/* Excel 업로드 */}
      <div style={styles.excelRow}>
        <label style={styles.uploadBtn}>
          Excel 업로드
          <input type="file" accept=".xlsx,.xls" style={{ display:'none' }} onChange={handleUpload} />
        </label>
        <button style={styles.tplBtn} onClick={handleTemplate}>템플릿 다운로드</button>
        {uploadResult && (
          <span style={{ fontSize:'13px', color: uploadResult.failCount > 0 ? 'orange' : 'green' }}>
            성공 {uploadResult.successCount} / 실패 {uploadResult.failCount}
          </span>
        )}
      </div>

      {/* 목록 */}
      <table style={styles.table}>
        <thead>
          <tr>
            {['작업지시 번호','설비','계획수량','양품수량','불량수량','진행률','상태','액션'].map(h => (
              <th key={h} style={styles.th}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {list.map(wo => {
            const total    = wo.goodQty + wo.defectQty;
            const progress = wo.plannedQty > 0 ? Math.min(100, Math.round(total / wo.plannedQty * 100)) : 0;
            return (
              <tr key={wo.id}>
                <td style={styles.td}>{wo.workOrderNo}</td>
                <td style={styles.td}>{wo.equipmentId}</td>
                <td style={styles.td}>{wo.plannedQty}</td>
                <td style={styles.td}><span style={{ color:'#52c41a', fontWeight:600 }}>{wo.goodQty}</span></td>
                <td style={styles.td}><span style={{ color: wo.defectQty > 0 ? '#ff4d4f' : '#999', fontWeight: wo.defectQty > 0 ? 600 : 400 }}>{wo.defectQty}</span></td>
                <td style={styles.td}>
                  <div style={styles.progressWrap}>
                    <div style={{ ...styles.progressBar, width: `${progress}%` }} />
                    <span style={styles.progressLabel}>{progress}%</span>
                  </div>
                </td>
                <td style={styles.td}>
                  <span style={{ ...styles.badge, background: statusColor(wo.status) }}>{wo.status}</span>
                </td>
                <td style={styles.td}>
                  {wo.status === 'PENDING' && (
                    <button style={styles.actionBtn} onClick={() => handleStatus(wo.id, 'IN_PROGRESS')}>시작</button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

const statusColor = (s) => ({ PENDING:'#faad14', IN_PROGRESS:'#1890ff', COMPLETED:'#52c41a', DEFECTIVE:'#ff4d4f' }[s] || '#ccc');

const styles = {
  form:          { display:'flex', gap:'8px', marginBottom:'12px' },
  input:         { padding:'8px', border:'1px solid #d9d9d9', borderRadius:'4px', fontSize:'13px' },
  btn:           { padding:'8px 16px', background:'#1890ff', color:'#fff', border:'none', borderRadius:'4px', cursor:'pointer' },
  excelRow:      { display:'flex', gap:'8px', alignItems:'center', marginBottom:'16px' },
  uploadBtn:     { padding:'6px 14px', background:'#52c41a', color:'#fff', borderRadius:'4px', cursor:'pointer', fontSize:'13px' },
  tplBtn:        { padding:'6px 14px', background:'#faad14', color:'#fff', border:'none', borderRadius:'4px', cursor:'pointer', fontSize:'13px' },
  table:         { width:'100%', borderCollapse:'collapse', background:'#fff', borderRadius:'8px', overflow:'hidden' },
  th:            { background:'#fafafa', padding:'12px', textAlign:'left', fontSize:'13px', borderBottom:'1px solid #f0f0f0' },
  td:            { padding:'12px', fontSize:'13px', borderBottom:'1px solid #f0f0f0' },
  badge:         { color:'#fff', padding:'2px 8px', borderRadius:'4px', fontSize:'12px' },
  actionBtn:     { padding:'4px 10px', background:'#1890ff', color:'#fff', border:'none', borderRadius:'4px', cursor:'pointer', fontSize:'12px' },
  progressWrap:  { position:'relative', background:'#f0f0f0', borderRadius:'4px', height:'16px', width:'100px', overflow:'hidden' },
  progressBar:   { position:'absolute', top:0, left:0, height:'100%', background:'#1890ff', borderRadius:'4px', transition:'width 0.3s' },
  progressLabel: { position:'absolute', top:0, left:0, width:'100%', textAlign:'center', fontSize:'11px', lineHeight:'16px', color:'#333', fontWeight:600 },
};

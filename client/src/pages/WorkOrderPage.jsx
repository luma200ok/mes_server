import { useEffect, useState, useMemo } from 'react';
import { getWorkOrders, createWorkOrder, changeStatus, uploadExcel, downloadTemplate } from '../api/workorder';

const PAGE_SIZE = 10;
const STATUS_TABS = [
  { value: '',            label: '전체' },
  { value: 'PENDING',     label: '대기' },
  { value: 'IN_PROGRESS', label: '진행중' },
  { value: 'COMPLETED',   label: '완료' },
  { value: 'DEFECTIVE',   label: '불량' },
];

export default function WorkOrderPage() {
  const [list, setList]           = useState([]);
  const [equipmentId, setEquipId] = useState('');
  const [plannedQty, setQty]      = useState('');
  const [uploadResult, setResult] = useState(null);

  const [statusFilter, setStatusFilter] = useState('');
  const [sortKey, setSortKey]           = useState('createdAt');
  const [sortAsc, setSortAsc]           = useState(false);
  const [page, setPage]                 = useState(0);

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

  useEffect(() => {
    const interval = setInterval(load, 30000);
    return () => clearInterval(interval);
  }, []);

  const handleTabChange = (val) => { setStatusFilter(val); setPage(0); };

  const handleSort = (key) => {
    if (sortKey === key) setSortAsc(v => !v);
    else { setSortKey(key); setSortAsc(true); }
    setPage(0);
  };

  const filtered = useMemo(() => {
    let data = statusFilter ? list.filter(wo => wo.status === statusFilter) : [...list];
    data.sort((a, b) => {
      const av = a[sortKey] ?? '';
      const bv = b[sortKey] ?? '';
      return sortAsc ? (av > bv ? 1 : -1) : (av < bv ? 1 : -1);
    });
    return data;
  }, [list, statusFilter, sortKey, sortAsc]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const paged = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

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

  const SortIcon = ({ col }) => {
    if (sortKey !== col) return <span style={{ color: '#ccc', marginLeft: 4 }}>↕</span>;
    return <span style={{ color: '#1890ff', marginLeft: 4 }}>{sortAsc ? '↑' : '↓'}</span>;
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

      {/* 상태 필터 탭 */}
      <div style={styles.tabRow}>
        {STATUS_TABS.map(tab => (
          <button
            key={tab.value}
            style={{ ...styles.tabBtn, ...(statusFilter === tab.value ? styles.tabBtnActive : {}) }}
            onClick={() => handleTabChange(tab.value)}
          >
            {tab.label}
            <span style={{
              ...styles.tabCount,
              background: statusFilter === tab.value ? '#1890ff' : '#e8e8e8',
              color: statusFilter === tab.value ? '#fff' : '#666',
            }}>
              {tab.value ? list.filter(wo => wo.status === tab.value).length : list.length}
            </span>
          </button>
        ))}
      </div>

      {/* 목록 */}
      <table style={styles.table}>
        <thead>
          <tr>
            <th style={{ ...styles.th, cursor: 'pointer' }} onClick={() => handleSort('workOrderNo')}>
              작업지시 번호<SortIcon col="workOrderNo" />
            </th>
            <th style={styles.th}>설비</th>
            <th style={styles.th}>계획수량</th>
            <th style={styles.th}>양품수량</th>
            <th style={styles.th}>불량수량</th>
            <th style={styles.th}>진행률</th>
            <th style={styles.th}>상태</th>
            <th style={{ ...styles.th, cursor: 'pointer' }} onClick={() => handleSort('createdAt')}>
              생성일<SortIcon col="createdAt" />
            </th>
            <th style={styles.th}>액션</th>
          </tr>
        </thead>
        <tbody>
          {paged.length === 0 ? (
            <tr><td colSpan={9} style={{ ...styles.td, textAlign: 'center', color: '#aaa' }}>데이터 없음</td></tr>
          ) : paged.map(wo => {
            const total    = wo.goodQty + wo.defectQty;
            const progress = wo.plannedQty > 0 ? Math.min(100, Math.round(total / wo.plannedQty * 100)) : 0;
            return (
              <tr key={wo.id}>
                <td style={styles.td}>{wo.workOrderNo}</td>
                <td style={styles.td}>{wo.equipmentId}</td>
                <td style={styles.td}>{wo.plannedQty}</td>
                <td style={styles.td}><span style={{ color: '#52c41a', fontWeight: 600 }}>{wo.goodQty}</span></td>
                <td style={styles.td}><span style={{ color: wo.defectQty > 0 ? '#ff4d4f' : '#999', fontWeight: wo.defectQty > 0 ? 600 : 400 }}>{wo.defectQty}</span></td>
                <td style={styles.td}>
                  <div style={styles.progressWrap}>
                    <div style={{ ...styles.progressBar, width: `${progress}%` }} />
                    <span style={styles.progressLabel}>{progress}%</span>
                  </div>
                </td>
                <td style={styles.td}>
                  <span style={{ ...styles.badge, background: statusColor(wo.status) }}>{statusLabel(wo.status)}</span>
                </td>
                <td style={{ ...styles.td, color: '#888', fontSize: '12px' }}>
                  {wo.createdAt ? new Date(wo.createdAt).toLocaleDateString('ko-KR') : '-'}
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

      {/* 페이징 */}
      <div style={styles.pagination}>
        <button style={styles.pageBtn} onClick={() => setPage(0)} disabled={page === 0}>«</button>
        <button style={styles.pageBtn} onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}>‹</button>
        <span style={styles.pageInfo}>{page + 1} / {totalPages} 페이지 ({filtered.length}건)</span>
        <button style={styles.pageBtn} onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}>›</button>
        <button style={styles.pageBtn} onClick={() => setPage(totalPages - 1)} disabled={page >= totalPages - 1}>»</button>
      </div>
    </div>
  );
}

const statusColor = (s) => ({ PENDING: '#faad14', IN_PROGRESS: '#1890ff', COMPLETED: '#52c41a', DEFECTIVE: '#ff4d4f' }[s] || '#ccc');
const statusLabel = (s) => ({ PENDING: '대기', IN_PROGRESS: '진행중', COMPLETED: '완료', DEFECTIVE: '불량' }[s] || s);

const styles = {
  form:          { display: 'flex', gap: '8px', marginBottom: '12px' },
  input:         { padding: '8px', border: '1px solid #d9d9d9', borderRadius: '4px', fontSize: '13px' },
  btn:           { padding: '8px 16px', background: '#1890ff', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer' },
  excelRow:      { display: 'flex', gap: '8px', alignItems: 'center', marginBottom: '16px' },
  uploadBtn:     { padding: '6px 14px', background: '#52c41a', color: '#fff', borderRadius: '4px', cursor: 'pointer', fontSize: '13px' },
  tplBtn:        { padding: '6px 14px', background: '#faad14', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '13px' },
  tabRow:        { display: 'flex', gap: '6px', marginBottom: '12px', flexWrap: 'wrap' },
  tabBtn:        { padding: '6px 14px', border: '1px solid #d9d9d9', borderRadius: '20px', background: '#fff', cursor: 'pointer', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '6px', color: '#595959' },
  tabBtnActive:  { borderColor: '#1890ff', color: '#1890ff', fontWeight: 600 },
  tabCount:      { borderRadius: '10px', padding: '1px 7px', fontSize: '11px', fontWeight: 600 },
  table:         { width: '100%', borderCollapse: 'collapse', background: '#fff', borderRadius: '8px', overflow: 'hidden' },
  th:            { background: '#fafafa', padding: '12px', textAlign: 'left', fontSize: '13px', borderBottom: '1px solid #f0f0f0', userSelect: 'none' },
  td:            { padding: '12px', fontSize: '13px', borderBottom: '1px solid #f0f0f0' },
  badge:         { color: '#fff', padding: '2px 8px', borderRadius: '4px', fontSize: '12px' },
  actionBtn:     { padding: '4px 10px', background: '#1890ff', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '12px' },
  progressWrap:  { position: 'relative', background: '#f0f0f0', borderRadius: '4px', height: '16px', width: '100px', overflow: 'hidden' },
  progressBar:   { position: 'absolute', top: 0, left: 0, height: '100%', background: '#1890ff', borderRadius: '4px', transition: 'width 0.3s' },
  progressLabel: { position: 'absolute', top: 0, left: 0, width: '100%', textAlign: 'center', fontSize: '11px', lineHeight: '16px', color: '#333', fontWeight: 600 },
  pagination:    { display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', marginTop: '16px' },
  pageBtn:       { padding: '4px 10px', border: '1px solid #d9d9d9', borderRadius: '4px', background: '#fff', cursor: 'pointer', fontSize: '13px' },
  pageInfo:      { fontSize: '13px', color: '#595959', minWidth: '150px', textAlign: 'center' },
};

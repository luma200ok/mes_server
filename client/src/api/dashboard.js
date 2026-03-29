import api from './axiosInstance';

export const getOee           = (equipmentId, date)             => api.get('/api/dashboard/oee', { params: { equipmentId, date } });
export const getSensorHistory = (equipmentId, from, to)         => api.get('/api/dashboard/sensor-history', { params: { equipmentId, from, to } });
export const exportExcel      = (equipmentId, from, to)         => api.get('/api/dashboard/export/excel', { params: { equipmentId, from, to }, responseType: 'blob' });
export const exportCsv        = (equipmentId, from, to)         => api.get('/api/dashboard/export/csv',   { params: { equipmentId, from, to }, responseType: 'blob' });

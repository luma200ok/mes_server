import api from './axiosInstance';

export const getDefects          = (params = {}) => api.get('/api/defects', { params });
export const getDefectsByWorkOrder = (workOrderId) => api.get(`/api/defects/by-work-order/${workOrderId}`);
export const getDefectSummary    = (params = {}) => api.get('/api/defects/summary', { params });
export const createDefect        = (data)        => api.post('/api/defects', data);

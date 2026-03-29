import api from './axiosInstance';

export const getDefects    = (workOrderId) => api.get('/api/defects', { params: workOrderId ? { workOrderId } : {} });
export const createDefect  = (data)        => api.post('/api/defects', data);

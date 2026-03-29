import api from './axiosInstance';

export const getWorkOrders   = ()         => api.get('/api/work-orders');
export const createWorkOrder = (data)     => api.post('/api/work-orders', data);
export const changeStatus    = (id, data) => api.patch(`/api/work-orders/${id}/status`, data);
export const getHistory      = (id)       => api.get(`/api/work-orders/${id}/history`);
export const uploadExcel     = (file)     => {
  const form = new FormData();
  form.append('file', file);
  return api.post('/api/work-orders/upload', form);
};
export const downloadTemplate = () =>
  api.get('/api/work-orders/upload/template', { responseType: 'blob' });

import api from './axiosInstance';

export const getEquipments         = ()          => api.get('/api/equipment');
export const createEquipment       = (data)      => api.post('/api/equipment', data);
export const deleteEquipment       = (equipmentId) => api.delete(`/api/equipment/${equipmentId}`);
export const getEquipmentConfig    = (equipmentId) => api.get(`/api/equipment-config/${equipmentId}`);
export const saveEquipmentConfig   = (data)      => api.post('/api/equipment-config', data);

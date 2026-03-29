import api from './axiosInstance';

export const getAlarmsByEquipment = (equipmentId, page = 0) =>
  api.get(`/api/alarms/equipment/${equipmentId}`, { params: { page } });

export const getAlarmsByPeriod = (from, to, page = 0) =>
  api.get('/api/alarms', { params: { from, to, page } });

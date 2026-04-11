import api from './axiosInstance';

export const getUsers = () => api.get('/api/users');

export const updateRole = (userId, role) =>
  api.patch(`/api/users/${userId}/role`, { role });

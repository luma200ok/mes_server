import api from './axiosInstance';

export const login = (username, password) =>
  api.post('/api/auth/login', { username, password });

export const register = (username, password, role) =>
  api.post('/api/auth/register', { username, password, role });

import api from './axiosInstance';

export const login = (username, password) =>
  api.post('/api/auth/login', { username, password });

import type { AxiosError, InternalAxiosRequestConfig } from "axios";
import axiosInstance from "./api";
import LoginService from './login.service';

const ACCESS_TOKEN_KEY = "access_token";

const setup = () => {
  axiosInstance.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      const token = localStorage.getItem(ACCESS_TOKEN_KEY);
      if (token) {
        config.headers['Authorization'] = `Bearer ${token}`;
      }
      return config;
    },
    (error: AxiosError) => {
      return Promise.reject(error);
    }
  );

  axiosInstance.interceptors.response.use(
    (res) => {
      return res;
    },
    async (err: AxiosError) => {
      const originalConfig = err.config as InternalAxiosRequestConfig & { _retry?: boolean };

      if (err.response && err.response.status === 401 && !originalConfig._retry) {
        originalConfig._retry = true;

        try {
          await LoginService.refreshToken();
          return axiosInstance(originalConfig);
        } catch (_error) {
          return Promise.reject(_error);
        }
      }

      return Promise.reject(err);
    }
  );
};

export default setup;

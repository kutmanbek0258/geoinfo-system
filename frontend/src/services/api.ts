import axios, { AxiosInstance } from "axios";

const serverURL = import.meta.env.VITE_API_GATEWAY_URL || '/api';

const instance: AxiosInstance = axios.create({
  baseURL: serverURL,
  headers: {
    "Content-Type": "application/json",
  },
});

export default instance;

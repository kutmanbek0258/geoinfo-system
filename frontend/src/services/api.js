import axios from "axios";
const serverURL = import.meta.env.API_GATEWAY_URL;

const instance = axios.create({
  baseURL: serverURL,
  headers: {
    "Content-Type": "application/json",
  },
});

export default instance;

import axios from "axios";
import router from '@/router';

const serverUrl = import.meta.env.VITE_AUTH_SERVICE_URL || 'http://localhost:9001/auth';
const clientId = 'test-client';
const authHeaderValue = 'Basic dGVzdC1jbGllbnQ6dGVzdC1jbGllbnQ=';
const redirectUri = import.meta.env.VITE_AUTH_CALLBACK_URL || 'http://localhost:8080/code';

const ACCESS_TOKEN_KEY = "access_token";
const REFRESH_TOKEN_KEY = "refresh_token";

class LoginService {
    login() {
        const requestParams = new URLSearchParams({
            response_type: "code",
            client_id: clientId,
            redirect_uri: redirectUri,
            scope: 'SSO.USER_PROFILE_INFO SSO.USER_AVATAR SSO.USER_IDENTIFICATION SSO.USER_AUTHORITIES'
        });
        window.location.href = `http://127.0.0.1:80/oauth2/authorize?${requestParams}`;
    }

    async refreshToken() {
        const payload = new FormData();
        payload.append('grant_type', 'refresh_token');
        payload.append('refresh_token', localStorage.getItem(REFRESH_TOKEN_KEY) || '');

        try {
            const response = await axios.post(`${serverUrl}/oauth2/token`, payload, {
                headers: {
                    'Content-type': 'application/x-www-form-urlencoded',
                    'Authorization': authHeaderValue
                }
            });
            localStorage.setItem(ACCESS_TOKEN_KEY, response.data[ACCESS_TOKEN_KEY]);
        } catch (error) {
            this.logout();
            this.login();
        }
    }

    async getTokens(code: string) {
        const payload = new FormData();
        payload.append('grant_type', 'authorization_code');
        payload.append('code', code);
        payload.append('redirect_uri', redirectUri);
        payload.append('client_id', clientId);

        try {
            const response = await axios.post(`${serverUrl}/oauth2/token`, payload, {
                headers: {
                    'Content-type': 'application/x-www-form-urlencoded',
                    'Authorization': authHeaderValue
                }
            });
            localStorage.setItem(ACCESS_TOKEN_KEY, response.data[ACCESS_TOKEN_KEY]);
            localStorage.setItem(REFRESH_TOKEN_KEY, response.data[REFRESH_TOKEN_KEY]);
            localStorage.setItem('loggedIn', "true");
            await router.push({ name: 'Dashboard' });
        } catch (error) {
            console.error('Error getting tokens:', (error as any).response?.data);
        }
    }

    getTokenInfo() {
        const payload = new FormData();
        payload.append('token', localStorage.getItem(ACCESS_TOKEN_KEY) || '');

        return axios.post(`${serverUrl}/oauth2/token-info`, payload, {
            headers: {
                'Authorization': authHeaderValue
            }
        });
    }

    logout() {
        localStorage.removeItem("loggedIn");
        localStorage.removeItem(ACCESS_TOKEN_KEY);
        localStorage.removeItem(REFRESH_TOKEN_KEY);
        // Возможно, стоит добавить редирект на страницу логина
        // router.push('/auth/login');
    }
}

export default new LoginService();

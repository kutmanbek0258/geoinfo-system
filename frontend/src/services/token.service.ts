interface User {
    accessToken: string;
    refreshToken: string;
    // ... other user properties
}

class TokenService {
  getLocalRefreshToken(): string | null {
    const userStr = localStorage.getItem("user");
    if (userStr) {
        const user: User = JSON.parse(userStr);
        return user?.refreshToken;
    }
    return null;
  }

  getLocalAccessToken(): string | null {
    const userStr = localStorage.getItem("user");
    if (userStr) {
        const user: User = JSON.parse(userStr);
        return user?.accessToken;
    }
    return null;
  }

  updateLocalAccessToken(token: string) {
    const userStr = localStorage.getItem("user");
    if (userStr) {
        let user: User = JSON.parse(userStr);
        user.accessToken = token;
        localStorage.setItem("user", JSON.stringify(user));
    }
  }

  getUser(): User | null {
    const userStr = localStorage.getItem("user");
    if (userStr) {
        return JSON.parse(userStr);
    }
    return null;
  }

  setUser(user: User) {
    localStorage.setItem("user", JSON.stringify(user));
  }

  removeUser() {
    localStorage.removeItem("user");
  }
}

export default new TokenService();

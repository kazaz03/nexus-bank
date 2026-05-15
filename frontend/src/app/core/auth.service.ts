import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface LoginResponse {
  token: string;
  userId: number;
  email: string;
  role: string;
  expiresIn: number;
}

const TOKEN_KEY = 'nexus.token';
const ROLE_KEY  = 'nexus.role';
const EMAIL_KEY = 'nexus.email';

/**
 * Talks to the API gateway (port 8080). The gateway routes /api/auth/login
 * to user-service. After login the JWT is stored in localStorage and
 * attached by AuthInterceptor on every outgoing request.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private readonly apiBase = 'http://localhost:8080';

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.apiBase}/api/auth/login`, { email, password })
      .pipe(tap(res => {
        localStorage.setItem(TOKEN_KEY, res.token);
        localStorage.setItem(ROLE_KEY, res.role);
        localStorage.setItem(EMAIL_KEY, res.email);
      }));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ROLE_KEY);
    localStorage.removeItem(EMAIL_KEY);
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem(TOKEN_KEY);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  getRole(): string | null {
    return localStorage.getItem(ROLE_KEY);
  }

  getEmail(): string | null {
    return localStorage.getItem(EMAIL_KEY);
  }
}

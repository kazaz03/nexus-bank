import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, EMPTY, Observable, of, switchMap, tap } from 'rxjs';

export interface LoginResponse {
  token: string;
  userId: number;
  email: string;
  role: string;
  expiresIn: number;
}

interface CustomerMeResponse {
  id: number;            // customerId
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
}

const TOKEN_KEY    = 'nexus.token';
const ROLE_KEY     = 'nexus.role';
const EMAIL_KEY    = 'nexus.email';
const USER_ID_KEY  = 'nexus.userId';
const CUSTOMER_ID_KEY = 'nexus.customerId';

/**
 * Talks to the API gateway (port 8080). The gateway routes /api/auth/login
 * to user-service. After login the JWT is stored in localStorage and
 * attached by AuthInterceptor on every outgoing request.
 *
 * For CUSTOMER users an additional GET /api/customers/me call resolves
 * the user id to a customer id
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private readonly apiBase = 'http://localhost:8080';
  login(email: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.apiBase}/api/auth/login`, { email, password })
      .pipe(
        tap(res => {
          localStorage.setItem(TOKEN_KEY, res.token);
          localStorage.setItem(ROLE_KEY, res.role);
          localStorage.setItem(EMAIL_KEY, res.email);
          localStorage.setItem(USER_ID_KEY, String(res.userId));
          localStorage.removeItem(CUSTOMER_ID_KEY); // clear stale value
        }),
        switchMap(res => {
          if (res.role !== 'CUSTOMER') {
            return of(res);
          }
          return this.http.get<CustomerMeResponse>(`${this.apiBase}/api/customers/me`).pipe(
            tap(profile => localStorage.setItem(CUSTOMER_ID_KEY, String(profile.id))),
            catchError(() => of(null)),
            switchMap(() => of(res))
          );
        })
      );
  }

  logout(): void {
    // Tell the server to revoke the token before clearing local state.
    // Fire-and-forget: local storage is cleared regardless of the response.
    this.http.post(`${this.apiBase}/api/auth/logout`, null)
      .pipe(catchError(() => EMPTY))
      .subscribe();

    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ROLE_KEY);
    localStorage.removeItem(EMAIL_KEY);
    localStorage.removeItem(USER_ID_KEY);
    localStorage.removeItem(CUSTOMER_ID_KEY);
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

  getUserId(): number | null {
    const raw = localStorage.getItem(USER_ID_KEY);
    return raw ? Number(raw) : null;
  }

  getCustomerId(): number | null {
    const raw = localStorage.getItem(CUSTOMER_ID_KEY);
    return raw ? Number(raw) : null;
  }
}

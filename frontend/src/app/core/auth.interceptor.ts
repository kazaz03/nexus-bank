import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Reads the JWT from localStorage and adds it as Authorization: Bearer <token>
 * on every outgoing request. The API gateway validates the token and forwards
 * X-User-Id / X-User-Role headers to downstream services.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('nexus.token');
  if (!token) {
    return next(req);
  }
  const authReq = req.clone({
    setHeaders: { Authorization: `Bearer ${token}` }
  });
  return next(authReq);
};

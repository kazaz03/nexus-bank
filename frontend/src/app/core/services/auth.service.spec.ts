import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const TOKEN_KEY       = 'nexus.token';
  const ROLE_KEY        = 'nexus.role';
  const EMAIL_KEY       = 'nexus.email';
  const USER_ID_KEY     = 'nexus.userId';
  const CUSTOMER_ID_KEY = 'nexus.customerId';

  const fakeLoginResponse = {
    token: 'fake-jwt',
    userId: 7,
    email: 'admin@nexusbank.com',
    role: 'ADMIN',
    expiresIn: 86400000,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  describe('login()', () => {
    it('stores token, role, email and userId for a non-CUSTOMER login', () => {
      service.login('admin@nexusbank.com', 'admin123').subscribe();

      const req = httpMock.expectOne('/api/auth/login');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ email: 'admin@nexusbank.com', password: 'admin123' });
      req.flush(fakeLoginResponse);

      // Should NOT call /api/customers/me for ADMIN
      httpMock.expectNone('/api/customers/me');

      expect(localStorage.getItem(TOKEN_KEY)).toBe('fake-jwt');
      expect(localStorage.getItem(ROLE_KEY)).toBe('ADMIN');
      expect(localStorage.getItem(EMAIL_KEY)).toBe('admin@nexusbank.com');
      expect(localStorage.getItem(USER_ID_KEY)).toBe('7');
    });

    it('calls /api/customers/me and stores customerId for CUSTOMER role', () => {
      const customerLoginRes = { ...fakeLoginResponse, role: 'CUSTOMER', email: 'marko@nexusbank.com' };
      service.login('marko@nexusbank.com', 'password1').subscribe();

      const loginReq = httpMock.expectOne('/api/auth/login');
      loginReq.flush(customerLoginRes);

      const meReq = httpMock.expectOne('/api/customers/me');
      expect(meReq.request.method).toBe('GET');
      meReq.flush({ id: 3, userId: 7, email: 'marko@nexusbank.com', firstName: 'Marko', lastName: 'Nikolic' });

      expect(localStorage.getItem(ROLE_KEY)).toBe('CUSTOMER');
      expect(localStorage.getItem(CUSTOMER_ID_KEY)).toBe('3');
    });

    it('clears nexus.customerId for non-CUSTOMER logins', () => {
      localStorage.setItem(CUSTOMER_ID_KEY, '99');
      service.login('admin@nexusbank.com', 'admin123').subscribe();
      httpMock.expectOne('/api/auth/login').flush(fakeLoginResponse);
      expect(localStorage.getItem(CUSTOMER_ID_KEY)).toBeNull();
    });
  });

  describe('logout()', () => {
    it('clears all five localStorage keys', () => {
      localStorage.setItem(TOKEN_KEY, 'tok');
      localStorage.setItem(ROLE_KEY, 'ADMIN');
      localStorage.setItem(EMAIL_KEY, 'x@y.com');
      localStorage.setItem(USER_ID_KEY, '1');
      localStorage.setItem(CUSTOMER_ID_KEY, '2');

      service.logout();
      // Flush or ignore the POST /api/auth/logout
      const req = httpMock.expectOne('/api/auth/logout');
      req.flush(null, { status: 204, statusText: 'No Content' });

      expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
      expect(localStorage.getItem(ROLE_KEY)).toBeNull();
      expect(localStorage.getItem(EMAIL_KEY)).toBeNull();
      expect(localStorage.getItem(USER_ID_KEY)).toBeNull();
      expect(localStorage.getItem(CUSTOMER_ID_KEY)).toBeNull();
    });
  });

  describe('getters', () => {
    it('isLoggedIn() returns true when token is in localStorage', () => {
      localStorage.setItem(TOKEN_KEY, 'tok');
      expect(service.isLoggedIn()).toBeTrue();
    });

    it('isLoggedIn() returns false when token is absent', () => {
      expect(service.isLoggedIn()).toBeFalse();
    });

    it('getCustomerId() parses stored string as a number', () => {
      localStorage.setItem(CUSTOMER_ID_KEY, '42');
      expect(service.getCustomerId()).toBe(42);
    });

    it('getCustomerId() returns null when key is absent', () => {
      expect(service.getCustomerId()).toBeNull();
    });
  });
});

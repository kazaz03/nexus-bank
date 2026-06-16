import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { roleGuard } from './role.guard';
import { AuthService } from '../services/auth.service';

describe('roleGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getRole']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  function runGuard(role: string | null, allowedRoles: string[]): boolean {
    const route = { data: { roles: allowedRoles } } as unknown as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;
    authServiceSpy.getRole.and.returnValue(role);
    return TestBed.runInInjectionContext(() => roleGuard(route, state)) as boolean;
  }

  it('allows access when user role is in allowed list', () => {
    const result = runGuard('ADMIN', ['LOAN_OFFICER', 'ADMIN']);
    expect(result).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('blocks access and navigates to /access-denied when role is not allowed', () => {
    const result = runGuard('CUSTOMER', ['LOAN_OFFICER', 'ADMIN']);
    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/access-denied']);
  });

  it('blocks access when user has no role (null)', () => {
    const result = runGuard(null, ['TELLER', 'ADMIN']);
    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/access-denied']);
  });

  it('allows access when route has no roles restriction (empty array)', () => {
    const result = runGuard('CUSTOMER', []);
    expect(result).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('allows TELLER access to a route that lists TELLER', () => {
    const result = runGuard('TELLER', ['TELLER', 'ADMIN']);
    expect(result).toBeTrue();
  });
});

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Restricts a route to the roles listed in its `data.roles`.
 * Assumes authGuard already ran (user is logged in). If the current
 * role is not allowed, redirects to /access-denied.
 *
 * Usage:
 *   { path: 'loans', component: LoansComponent,
 *     canActivate: [authGuard, roleGuard], data: { roles: ['LOAN_OFFICER', 'ADMIN'] } }
 */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const allowed = (route.data?.['roles'] as string[] | undefined) ?? [];
  const role = auth.getRole();

  if (allowed.length === 0 || (role && allowed.includes(role))) {
    return true;
  }

  router.navigate(['/access-denied']);
  return false;
};

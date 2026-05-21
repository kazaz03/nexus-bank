import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login';
import { DashboardComponent } from './pages/dashboard/dashboard';
import { LoansComponent } from './pages/loans/loans';
import { AccountsComponent } from './pages/accounts/accounts';
import { TransferComponent } from './pages/transfer/transfer';
import { authGuard } from './core/auth.guard';
export const routes: Routes = [
  { path: '',           redirectTo: '/login', pathMatch: 'full' },
  { path: 'login',      component: LoginComponent },
  { path: 'dashboard',  component: DashboardComponent, canActivate: [authGuard] },
  { path: 'accounts',   component: AccountsComponent,  canActivate: [authGuard] },
  { path: 'transfer',   component: TransferComponent,  canActivate: [authGuard] },
  { path: 'loans',      component: LoansComponent,     canActivate: [authGuard] },
  { path: '**',         redirectTo: '/login' }
];

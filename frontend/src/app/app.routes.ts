import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login';
import { DashboardComponent } from './features/dashboard/dashboard';
import { LoansComponent } from './features/loans/loans';
import { AccountsComponent } from './features/accounts/accounts';
import { TransferComponent } from './features/transfer/transfer';
import { TransactionsComponent } from './features/transactions/transactions';
import { ApplyLoanComponent } from './features/apply-loan/apply-loan';
import { LoanConfirmationComponent } from './features/loan-confirmation/loan-confirmation';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '',                          redirectTo: '/login', pathMatch: 'full' },
  { path: 'login',                     component: LoginComponent },
  { path: 'dashboard',                 component: DashboardComponent,    canActivate: [authGuard] },
  { path: 'accounts',                  component: AccountsComponent,     canActivate: [authGuard] },
  { path: 'transfer',                  component: TransferComponent,     canActivate: [authGuard] },
  { path: 'loans',                     component: LoansComponent,        canActivate: [authGuard] },
  { path: 'transactions/:accountId',   component: TransactionsComponent, canActivate: [authGuard] },
  { path: 'apply-loan',                component: ApplyLoanComponent,    canActivate: [authGuard] },
  { path: 'loan-confirmation',         component: LoanConfirmationComponent, canActivate: [authGuard] },
  { path: '**',                        redirectTo: '/login' }
];

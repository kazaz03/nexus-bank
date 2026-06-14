import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login';
import { DashboardComponent } from './features/dashboard/dashboard';
import { LoansComponent } from './features/loans/loans';
import { AccountsComponent } from './features/accounts/accounts';
import { CardsComponent } from './features/cards/cards';
import { ProfileComponent } from './features/profile/profile';
import { TransferComponent } from './features/transfer/transfer';
import { TransactionsComponent } from './features/transactions/transactions';
import { ApplyLoanComponent } from './features/apply-loan/apply-loan';
import { LoanConfirmationComponent } from './features/loan-confirmation/loan-confirmation';
import { MyLoansComponent } from './features/my-loans/my-loans';
import { CustomerAccountsComponent } from './features/customer-accounts/customer-accounts';
import { StatementComponent } from './features/statement/statement';
import { ExchangeRatesComponent } from './features/exchange-rates/exchange-rates';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '',                                     redirectTo: '/login', pathMatch: 'full' },
  { path: 'login',                                component: LoginComponent },
  { path: 'dashboard',                            component: DashboardComponent,          canActivate: [authGuard] },
  { path: 'accounts',                             component: AccountsComponent,           canActivate: [authGuard] },
  { path: 'cards',                                component: CardsComponent,              canActivate: [authGuard] },
  { path: 'profile',                              component: ProfileComponent,            canActivate: [authGuard] },
  { path: 'transfer',                             component: TransferComponent,           canActivate: [authGuard] },
  { path: 'loans',                                component: LoansComponent,              canActivate: [authGuard] },
  { path: 'transactions/:accountId',              component: TransactionsComponent,       canActivate: [authGuard] },
  { path: 'apply-loan',                           component: ApplyLoanComponent,          canActivate: [authGuard] },
  { path: 'loan-confirmation',                    component: LoanConfirmationComponent,   canActivate: [authGuard] },
  { path: 'my-loans',                             component: MyLoansComponent,            canActivate: [authGuard] },
  { path: 'customers/:customerId/accounts',       component: CustomerAccountsComponent,   canActivate: [authGuard] },
  { path: 'statement/:accountId',                 component: StatementComponent,          canActivate: [authGuard] },
  { path: 'exchange-rates',                       component: ExchangeRatesComponent,      canActivate: [authGuard] },
  { path: '**',                                   redirectTo: '/login' }
];

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
// HttpClientTestingModule satisfies any HttpClient injected by services under test.
import { Router } from '@angular/router';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { DashboardComponent } from './dashboard';
import { AuthService } from '../../core/services/auth.service';
import { AccountService } from '../../services/account.service';
import { CustomerService } from '../../services/customer.service';
import { AdminService } from '../../services/admin.service';
import { TransactionService } from '../../services/transaction.service';
import { CardService } from '../../services/card.service';
import { Account } from '../../models/account.model';
import { of, throwError } from 'rxjs';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let authSpy: jasmine.SpyObj<AuthService>;
  let accountSpy: jasmine.SpyObj<AccountService>;
  let customerSpy: jasmine.SpyObj<CustomerService>;
  let adminSpy: jasmine.SpyObj<AdminService>;
  let txSpy: jasmine.SpyObj<TransactionService>;
  let cardSpy: jasmine.SpyObj<CardService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const fakeAccounts: Account[] = [
    { id: 1, iban: 'BA001', accountType: 'CHECKING', currency: 'BAM', balance: 500, status: 'ACTIVE' } as Account,
  ];

  function setupComponent(role: string, customerId: number | null = null): void {
    authSpy.getRole.and.returnValue(role);
    authSpy.getEmail.and.returnValue('test@nexusbank.com');
    authSpy.getCustomerId.and.returnValue(customerId);
    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
  }

  beforeEach(async () => {
    authSpy     = jasmine.createSpyObj('AuthService',     ['getRole', 'getEmail', 'getCustomerId', 'logout']);
    accountSpy  = jasmine.createSpyObj('AccountService',  ['getByCustomer', 'getById']);
    customerSpy = jasmine.createSpyObj('CustomerService', ['getAll', 'register', 'update', 'updateKyc']);
    adminSpy    = jasmine.createSpyObj('AdminService',    ['getStats']);
    txSpy       = jasmine.createSpyObj('TransactionService', ['getByAccount', 'deposit', 'withdraw']);
    cardSpy     = jasmine.createSpyObj('CardService',     ['getByAccount', 'issue', 'setStatus']);
    routerSpy   = jasmine.createSpyObj('Router',          ['navigate']);

    await TestBed.configureTestingModule({
      imports: [DashboardComponent, HttpClientTestingModule],
      providers: [
        { provide: AuthService,        useValue: authSpy },
        { provide: AccountService,     useValue: accountSpy },
        { provide: CustomerService,    useValue: customerSpy },
        { provide: AdminService,       useValue: adminSpy },
        { provide: TransactionService, useValue: txSpy },
        { provide: CardService,        useValue: cardSpy },
        { provide: Router,             useValue: routerSpy },
      ],
      // NO_ERRORS_SCHEMA prevents child standalone components (TopbarComponent,
      // NavTabsComponent) from failing compilation due to missing RouterModule etc.
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  });

  describe('CUSTOMER role', () => {
    it('calls accountService.getByCustomer() on init when customerId is available', () => {
      accountSpy.getByCustomer.and.returnValue(of(fakeAccounts));
      setupComponent('CUSTOMER', 42);
      fixture.detectChanges(); // triggers ngOnInit

      expect(accountSpy.getByCustomer).toHaveBeenCalledWith(42);
      expect(customerSpy.getAll).not.toHaveBeenCalled();
      expect(component.myAccounts()).toEqual(fakeAccounts);
    });

    it('sets myAccountsError when customerId is null', () => {
      setupComponent('CUSTOMER', null);
      fixture.detectChanges();

      expect(accountSpy.getByCustomer).not.toHaveBeenCalled();
      expect(component.myAccountsError()).toContain('Customer profile not found');
    });

    it('sets myAccountsError when HTTP call fails', () => {
      accountSpy.getByCustomer.and.returnValue(throwError(() => ({ status: 503 })));
      setupComponent('CUSTOMER', 5);
      fixture.detectChanges();

      expect(component.myAccountsError()).toContain('HTTP 503');
    });
  });

  describe('TELLER role', () => {
    it('calls customerService.getAll() on init', () => {
      customerSpy.getAll.and.returnValue(of([]));
      setupComponent('TELLER');
      fixture.detectChanges();

      expect(customerSpy.getAll).toHaveBeenCalled();
      expect(accountSpy.getByCustomer).not.toHaveBeenCalled();
    });
  });

  describe('ADMIN role', () => {
    it('calls both customerService.getAll() and adminService.getStats()', () => {
      customerSpy.getAll.and.returnValue(of([]));
      adminSpy.getStats.and.returnValue(of(null as any));
      setupComponent('ADMIN');
      fixture.detectChanges();

      expect(customerSpy.getAll).toHaveBeenCalled();
      expect(adminSpy.getStats).toHaveBeenCalled();
    });
  });

  describe('viewTransactions()', () => {
    it('navigates to /transactions/{accountId}', () => {
      accountSpy.getByCustomer.and.returnValue(of(fakeAccounts));
      setupComponent('CUSTOMER', 1);
      fixture.detectChanges();

      component.viewTransactions(7);
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/transactions', 7]);
    });
  });
});

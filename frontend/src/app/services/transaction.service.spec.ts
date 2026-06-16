import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TransactionService } from './transaction.service';
import { TransactionFilters } from '../models/transaction.model';

describe('TransactionService', () => {
  let service: TransactionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(TransactionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('getByAccount()', () => {
    it('builds URL with page and size when no filters are set', () => {
      const filters: TransactionFilters = { page: 0, size: 20 };
      service.getByAccount(5, filters).subscribe();

      const req = httpMock.expectOne(r => r.url.includes('/api/transactions/account/5'));
      expect(req.request.method).toBe('GET');
      expect(req.request.urlWithParams).toContain('page=0');
      expect(req.request.urlWithParams).toContain('size=20');
      req.flush({ content: [], totalElements: 0 });
    });

    it('appends type filter when provided', () => {
      const filters: TransactionFilters = { page: 0, size: 10, type: 'DEPOSIT' };
      service.getByAccount(7, filters).subscribe();

      const req = httpMock.expectOne(r => r.url.includes('/api/transactions/account/7'));
      expect(req.request.urlWithParams).toContain('type=DEPOSIT');
      req.flush({ content: [], totalElements: 0 });
    });

    it('appends T00:00:00 to from date and T23:59:59 to to date', () => {
      const filters: TransactionFilters = { page: 0, size: 20, from: '2024-01-01', to: '2024-01-31' };
      service.getByAccount(10, filters).subscribe();

      const req = httpMock.expectOne(r => r.url.includes('/api/transactions/account/10'));
      const url = decodeURIComponent(req.request.urlWithParams);
      expect(url).toContain('from=2024-01-01T00:00:00');
      expect(url).toContain('to=2024-01-31T23:59:59');
      req.flush({ content: [], totalElements: 0 });
    });
  });

  describe('getStatementPdf()', () => {
    it('uses responseType blob and constructs the correct URL', () => {
      service.getStatementPdf(42, '2024-06-01', '2024-06-30').subscribe();

      const req = httpMock.expectOne(r =>
        r.url.includes('/api/transactions/accounts/42/statement/pdf'));
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');

      const url = decodeURIComponent(req.request.urlWithParams);
      expect(url).toContain('from=2024-06-01T00:00:00');
      expect(url).toContain('to=2024-06-30T23:59:59');
      req.flush(new Blob(['%PDF'], { type: 'application/pdf' }));
    });
  });

  describe('transfer()', () => {
    it('POSTs to /api/transactions/transfer with the request body', () => {
      const body = { sourceIban: 'BA001', targetIban: 'BA002', amount: 100, initiatedBy: 1 };
      service.transfer(body as any).subscribe();

      const req = httpMock.expectOne('/api/transactions/transfer');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({ reference: 'TRX-ABC' });
    });
  });
});

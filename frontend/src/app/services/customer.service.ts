import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Customer } from '../models/customer.model';

@Injectable({ providedIn: 'root' })
export class CustomerService {
  private http = inject(HttpClient);
  private readonly apiBase = '';

  getAll(): Observable<Customer[]> {
    return this.http.get<Customer[]>(`${this.apiBase}/api/customers`);
  }
}

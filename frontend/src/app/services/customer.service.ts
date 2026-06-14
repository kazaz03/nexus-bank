import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Customer } from '../models/customer.model';

export interface UpdateCustomerRequest {
  firstName: string;
  lastName: string;
  address?: string;
  phone?: string;
}

export interface RegisterCustomerRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;   // yyyy-MM-dd
  idCardNumber: string;
  address?: string;
  phone?: string;
}

@Injectable({ providedIn: 'root' })
export class CustomerService {
  private http = inject(HttpClient);
  private readonly apiBase = '';

  getAll(): Observable<Customer[]> {
    return this.http.get<Customer[]>(`${this.apiBase}/api/customers`);
  }

  register(body: RegisterCustomerRequest): Observable<Customer> {
    return this.http.post<Customer>(`${this.apiBase}/api/customers`, body);
  }

  getMe(): Observable<Customer> {
    return this.http.get<Customer>(`${this.apiBase}/api/customers/me`);
  }

  update(id: number, body: UpdateCustomerRequest): Observable<Customer> {
    return this.http.put<Customer>(`${this.apiBase}/api/customers/${id}`, body);
  }
}

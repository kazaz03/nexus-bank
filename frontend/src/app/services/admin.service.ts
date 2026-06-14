import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AdminStats } from '../models/admin-stats.model';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);
  private readonly apiBase = '';

  getStats(): Observable<AdminStats> {
    return this.http.get<AdminStats>(`${this.apiBase}/api/admin/stats`);
  }
}

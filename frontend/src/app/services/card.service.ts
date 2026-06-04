import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DebitCard } from '../models/card.model';

@Injectable({ providedIn: 'root' })
export class CardService {
  private http = inject(HttpClient);
  private readonly apiBase = '';

  getByAccount(accountId: number): Observable<DebitCard[]> {
    return this.http.get<DebitCard[]>(`${this.apiBase}/api/accounts/${accountId}/cards`);
  }

  issueCard(accountId: number, issuedBy: number): Observable<DebitCard> {
    return this.http.post<DebitCard>(
      `${this.apiBase}/api/accounts/${accountId}/cards`,
      { issuedBy }
    );
  }

  blockCard(cardId: number): Observable<DebitCard> {
    return this.http.patch<DebitCard>(`${this.apiBase}/api/cards/${cardId}/block`, null);
  }

  unblockCard(cardId: number): Observable<DebitCard> {
    return this.http.patch<DebitCard>(`${this.apiBase}/api/cards/${cardId}/unblock`, null);
  }
}

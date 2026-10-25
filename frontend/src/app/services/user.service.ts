import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Role } from '../models/auth.model';
import { PageResponse } from '../models/page.model';
import { UserAccount } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/users`;

  // ******************************************
  // ************ GET ************
  // ******************************************

  search(q: string, role: Role | null, page: number, size: number): Observable<PageResponse<UserAccount>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    if (role) {
      params = params.set('role', role);
    }
    return this.http.get<PageResponse<UserAccount>>(this.url, { params });
  }

  // ******************************************
  // ************ UPDATE ************
  // ******************************************

  changeRole(id: string, role: Role): Observable<UserAccount> {
    return this.http.put<UserAccount>(`${this.url}/${id}/role`, { role });
  }

  /** clientId à null : le compte est détaché de sa fiche client. */
  linkClient(id: string, clientId: string | null): Observable<UserAccount> {
    return this.http.put<UserAccount>(`${this.url}/${id}/client`, { clientId });
  }
}

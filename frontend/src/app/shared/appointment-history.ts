import { DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { Button } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TableLazyLoadEvent } from 'primeng/types/table';
import { Tag } from 'primeng/tag';
import { formatMoney } from '../core/format';
import { appointmentSeverity } from '../core/status';
import { Appointment } from '../models/appointment.model';
import { PageResponse } from '../models/page.model';
import { NotificationService } from '../services/notification.service';
import { SettingsService } from '../services/settings.service';

export type AppointmentSource = (page: number, size: number) => Observable<PageResponse<Appointment>>;

/** Tableau paginé de rendez-vous (prestations) d'un client ou d'un véhicule. Le détail s'ouvre dans la page Rendez-vous. */
@Component({
  selector: 'app-appointment-history',
  imports: [DatePipe, TranslatePipe, TableModule, Button, Tag],
  templateUrl: './appointment-history.html',
  styleUrl: './appointment-history.css'
})
export class AppointmentHistory {
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  protected readonly settings = inject(SettingsService);

  readonly source = input.required<AppointmentSource>();
  /** Colonne véhicule : utile pour un client, redondante pour un véhicule. */
  readonly showVehicle = input(true);

  protected readonly items = signal<Appointment[]>([]);
  protected readonly total = signal(0);
  protected readonly first = signal(0);
  protected readonly rows = signal(10);
  protected readonly isLoading = signal(false);
  protected readonly hasError = signal(false);

  protected readonly severity = appointmentSeverity;

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.first.set(event.first ?? 0);
    this.rows.set(event.rows ?? 10);
    this.load();
  }

  protected load(): void {
    this.isLoading.set(true);
    this.hasError.set(false);
    const page = Math.floor(this.first() / this.rows());
    this.source()(page, this.rows()).subscribe({
      next: (result) => {
        this.items.set(result.content);
        this.total.set(result.totalElements);
        this.isLoading.set(false);
      },
      error: (error: unknown) => {
        this.items.set([]);
        this.isLoading.set(false);
        this.hasError.set(true);
        this.notifications.error(error);
      }
    });
  }

  protected money(value: number | null): string {
    return value === null || value === undefined ? '' : formatMoney(value, this.settings.language());
  }

  protected openAppointment(id: string): void {
    this.router.navigate(['/appointments'], { queryParams: { open: id } });
  }
}

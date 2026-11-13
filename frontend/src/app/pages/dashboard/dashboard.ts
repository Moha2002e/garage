import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { Message } from 'primeng/message';
import { ProgressBar } from 'primeng/progressbar';
import { Tag } from 'primeng/tag';
import { formatMoney } from '../../core/format';
import { appointmentSeverity } from '../../core/status';
import { Dashboard as DashboardData, DashboardAppointment } from '../../models/dashboard.model';
import { DashboardService } from '../../services/dashboard.service';
import { NotificationService } from '../../services/notification.service';
import { SettingsService } from '../../services/settings.service';

interface StatCard {
  key: string;
  value: string;
}

/** Accueil de l'ADMIN et de l'EMPLOYEE : ce qui se passe aujourd'hui, ce qui est en cours, ce qui arrive et l'argent. */
@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, TranslatePipe, Button, Card, Message, ProgressBar, Tag],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard {
  private readonly dashboardService = inject(DashboardService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  protected readonly settings = inject(SettingsService);

  protected readonly isLoading = signal(false);
  private readonly data = signal<DashboardData | null>(null);

  protected readonly todayList = computed<DashboardAppointment[]>(() => this.data()?.todayAppointments ?? []);
  protected readonly inProgressList = computed<DashboardAppointment[]>(() => this.data()?.inProgressNow ?? []);
  protected readonly upcomingList = computed<DashboardAppointment[]>(() => (this.data()?.upcoming ?? []).slice(0, 5));
  protected readonly pending = computed(() => this.data()?.pendingInvoiceCount ?? 0);
  protected readonly lowStockParts = computed(() => this.data()?.lowStockParts ?? 0);
  protected readonly unpaidCount = computed(() => this.data()?.unpaidInvoicesCount ?? 0);
  protected readonly unpaidAmount = computed(() =>
    formatMoney(this.data()?.unpaidInvoicesAmount, this.settings.language())
  );
  protected readonly monthRevenue = computed(() =>
    formatMoney(this.data()?.monthRevenue, this.settings.language())
  );

  // Les anciens chiffres qui servent encore.
  protected readonly cards = computed<StatCard[]>(() => {
    const d = this.data();
    return [
      { key: 'dashboard.plannedNext7Days', value: String(d?.plannedNext7Days ?? 0) },
      { key: 'dashboard.monthCompleted', value: String(d?.monthCompleted ?? 0) },
      { key: 'dashboard.activeClients', value: String(d?.activeClients ?? 0) },
      { key: 'dashboard.activeVehicles', value: String(d?.activeVehicles ?? 0) }
    ];
  });

  constructor() {
    this.load();
  }

  private load(): void {
    this.isLoading.set(true);
    this.dashboardService.get().subscribe({
      next: (result) => {
        this.data.set(result);
        this.isLoading.set(false);
      },
      error: (error: unknown) => {
        this.isLoading.set(false);
        this.notifications.error(error);
      }
    });
  }

  /** Une ligne ouvre le détail du rendez-vous dans la page des rendez-vous. */
  protected openAppointment(id: string): void {
    this.router.navigate(['/appointments'], { queryParams: { open: id } });
  }

  protected goParts(): void {
    this.router.navigate(['/parts']);
  }

  protected goInvoices(status?: 'ISSUED'): void {
    this.router.navigate(['/invoices'], { queryParams: status ? { status } : {} });
  }

  /** Avancement fourni par le back-end, sinon estimé avec l'heure de début et de fin. */
  protected progress(appointment: DashboardAppointment): number {
    const given = appointment.progressPercent;
    if (given !== null && given !== undefined) {
      return Math.max(0, Math.min(100, Math.round(given)));
    }
    const start = new Date(appointment.start).getTime();
    const end = new Date(appointment.end).getTime();
    if (!(end > start)) {
      return 0;
    }
    return Math.max(0, Math.min(100, Math.round(((Date.now() - start) / (end - start)) * 100)));
  }

  protected statusSeverity = appointmentSeverity;
}

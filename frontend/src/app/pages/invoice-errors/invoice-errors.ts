import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TableLazyLoadEvent } from 'primeng/types/table';
import { Button } from 'primeng/button';
import { Select } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { formatRunDuration } from '../../core/format';
import { batchSeverity } from '../../core/status';
import { BatchError, BatchRun } from '../../models/invoice.model';
import { InvoiceService } from '../../services/invoice.service';
import { NotificationService } from '../../services/notification.service';
import { SettingsService } from '../../services/settings.service';

const KNOWN_CODES = [
  'NO_ACCEPTED_QUOTE',
  'EMPTY_QUOTE',
  'INVALID_AMOUNT',
  'DUPLICATE_INVOICE',
  'UNEXPECTED'
];

/** Erreurs du batch de facturation : dernières exécutions, puis erreurs filtrables par exécution. */
@Component({
  selector: 'app-invoice-errors',
  imports: [DatePipe, FormsModule, RouterLink, TranslatePipe, TableModule, Button, Select, Tag],
  templateUrl: './invoice-errors.html',
  styleUrl: './invoice-errors.css'
})
export class InvoiceErrors {
  private readonly invoiceService = inject(InvoiceService);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly settings = inject(SettingsService);

  // Dernières exécutions
  protected readonly runs = signal<BatchRun[]>([]);
  protected readonly runsLoading = signal(false);

  // Erreurs
  protected readonly items = signal<BatchError[]>([]);
  protected readonly total = signal(0);
  protected readonly first = signal(0);
  protected readonly rows = signal(10);
  protected readonly isLoading = signal(false);
  protected readonly runFilter = signal<string | null>(
    this.route.snapshot.queryParamMap.get('runId')
  );

  protected readonly runOptions = computed(() => {
    const options: { value: string | number | null; label: string }[] = this.runs().map((run) => ({
      value: String(run.id),
      label: this.runLabel(run)
    }));
    const current = this.runFilter();
    if (current !== null && !options.some((o) => String(o.value) === String(current))) {
      options.push({ value: current, label: `#${current}` });
    }
    return [{ value: null, label: this.translate.instant('invoices.allRuns') }, ...options];
  });

  constructor() {
    this.loadRuns();
  }

  private loadRuns(): void {
    this.runsLoading.set(true);
    this.invoiceService.runs(0, 5).subscribe({
      next: (result) => {
        this.runs.set(result.content);
        this.runsLoading.set(false);
      },
      error: (error: unknown) => {
        this.runsLoading.set(false);
        this.notifications.error(error);
      }
    });
  }

  private runLabel(run: BatchRun): string {
    const date = new Date(run.startTime);
    const text = Number.isNaN(date.getTime())
      ? ''
      : date.toLocaleString(this.settings.language() === 'en' ? 'en-GB' : 'fr-BE', {
          dateStyle: 'short',
          timeStyle: 'short'
        });
    return `#${run.id} ${text}`.trim();
  }

  // ---------- Erreurs ----------

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.first.set(event.first ?? 0);
    this.rows.set(event.rows ?? 10);
    this.load();
  }

  protected onRunChange(runId: string | number | null): void {
    this.runFilter.set(runId === null ? null : String(runId));
    this.first.set(0);
    this.load();
  }

  private load(): void {
    this.isLoading.set(true);
    const page = Math.floor(this.first() / this.rows());
    this.invoiceService.errors(page, this.rows(), this.runFilter()).subscribe({
      next: (result) => {
        this.items.set(result.content);
        this.total.set(result.totalElements);
        this.isLoading.set(false);
      },
      error: (error: unknown) => {
        this.isLoading.set(false);
        this.notifications.error(error);
      }
    });
  }

  protected backToInvoices(): void {
    this.router.navigate(['/invoices']);
  }

  protected duration(run: BatchRun): string {
    return formatRunDuration(run.startTime, run.endTime);
  }

  /** Code connu : libellé traduit. Code inconnu : le code brut. */
  protected isKnown(code: string): boolean {
    return KNOWN_CODES.includes(code);
  }

  protected runSeverity = batchSeverity;
}

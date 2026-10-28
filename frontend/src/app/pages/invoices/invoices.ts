import { DatePipe } from '@angular/common';
import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TableLazyLoadEvent } from 'primeng/types/table';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { Subject, debounceTime } from 'rxjs';
import { formatMoney, formatRunDuration } from '../../core/format';
import { batchSeverity, invoiceSeverity } from '../../core/status';
import { BatchRun, Invoice, InvoiceDetail, InvoiceStatus } from '../../models/invoice.model';
import { AuthService } from '../../services/auth.service';
import { ExportService } from '../../services/export.service';
import { InvoiceService } from '../../services/invoice.service';
import { NotificationService } from '../../services/notification.service';
import { SettingsService } from '../../services/settings.service';

type DialogMode = 'view' | 'confirm';

@Component({
  selector: 'app-invoices',
  imports: [
    DatePipe,
    FormsModule,
    TranslatePipe,
    TableModule,
    Button,
    Dialog,
    IconField,
    InputIcon,
    InputText,
    Select,
    Tag
  ],
  templateUrl: './invoices.html',
  styleUrl: './invoices.css'
})
export class Invoices implements OnInit {
  private readonly invoiceService = inject(InvoiceService);
  private readonly exportService = inject(ExportService);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);
  protected readonly settings = inject(SettingsService);

  // Liste (déjà filtrée et paginée par le back-end)
  protected readonly items = signal<Invoice[]>([]);
  protected readonly total = signal(0);
  protected readonly first = signal(0);
  protected readonly rows = signal(10);
  protected readonly isLoading = signal(false);
  protected readonly search = signal('');
  protected readonly statusFilter = signal<InvoiceStatus | null>(null);
  private readonly searchInput = new Subject<string>();

  protected readonly statusOptions = computed(() => [
    { value: null, label: this.translate.instant('invoices.allStatuses') },
    { value: 'ISSUED', label: this.translate.instant('invoices.status.ISSUED') },
    { value: 'PAID', label: this.translate.instant('invoices.status.PAID') }
  ]);

  protected readonly hasFilter = computed(() => this.search().trim() !== '' || this.statusFilter() !== null);

  // Détail
  protected readonly dialogOpen = signal(false);
  protected readonly mode = signal<DialogMode>('view');
  protected readonly selected = signal<InvoiceDetail | null>(null);
  protected readonly isDetailLoading = signal(false);
  protected readonly isSaving = signal(false);
  protected readonly isDownloading = signal(false);

  // Batch
  protected readonly isRunning = signal(false);
  protected readonly runResult = signal<BatchRun | null>(null);
  protected readonly resultOpen = signal(false);

  protected readonly isExporting = signal(false);

  ngOnInit(): void {
    // Lien depuis l'accueil : ?status=ISSUED
    const status = this.route.snapshot.queryParamMap.get('status');
    if (status === 'ISSUED' || status === 'PAID') {
      this.statusFilter.set(status);
    }
    this.searchInput
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe((text) => {
        this.search.set(text);
        this.first.set(0);
        this.load();
      });
  }

  // ---------- Liste ----------

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.first.set(event.first ?? 0);
    this.rows.set(event.rows ?? 10);
    this.load();
  }

  protected onSearch(text: string): void {
    this.searchInput.next(text);
  }

  protected onStatusChange(status: InvoiceStatus | null): void {
    this.statusFilter.set(status);
    this.first.set(0);
    this.load();
  }

  private load(): void {
    this.isLoading.set(true);
    const page = Math.floor(this.first() / this.rows());
    this.invoiceService
      .search({ q: this.search(), status: this.statusFilter() }, page, this.rows())
      .subscribe({
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

  protected money(value: number): string {
    return formatMoney(value, this.settings.language());
  }

  protected duration(run: BatchRun): string {
    return formatRunDuration(run.startTime, run.endTime);
  }

  protected severity = invoiceSeverity;
  protected runSeverity = batchSeverity;

  // ---------- Détail ----------

  protected open(invoice: Invoice): void {
    this.selected.set({ ...invoice, lines: [] });
    this.mode.set('view');
    this.dialogOpen.set(true);
    this.isDetailLoading.set(true);
    this.invoiceService.get(invoice.id).subscribe({
      next: (detail) => {
        this.selected.set(detail);
        this.isDetailLoading.set(false);
      },
      error: (error: unknown) => {
        this.isDetailLoading.set(false);
        this.dialogOpen.set(false);
        this.notifications.error(error);
      }
    });
  }

  protected startPay(): void {
    this.mode.set('confirm');
  }

  protected backToView(): void {
    this.mode.set('view');
  }

  protected pay(): void {
    const invoice = this.selected();
    if (!invoice) {
      return;
    }
    this.isSaving.set(true);
    this.invoiceService.pay(invoice.id).subscribe({
      next: (paid) => {
        this.isSaving.set(false);
        this.selected.set({ ...invoice, ...paid, lines: paid.lines ?? invoice.lines });
        this.mode.set('view');
        this.notifications.success('invoices.paid', { number: paid.number });
        this.load();
      },
      error: (error: unknown) => {
        this.isSaving.set(false);
        this.mode.set('view');
        this.notifications.error(error);
        // 409 : déjà payée ailleurs, on rafraîchit la liste.
        this.load();
      }
    });
  }

  protected downloadPdf(): void {
    const invoice = this.selected();
    if (!invoice || this.isDownloading()) {
      return;
    }
    this.isDownloading.set(true);
    this.invoiceService.pdf(invoice.id).subscribe({
      next: (blob) => {
        this.isDownloading.set(false);
        this.saveFile(blob, `${invoice.number.replace(/[\\/:*?"<>|]/g, '_')}.pdf`);
      },
      error: (error: unknown) => {
        this.isDownloading.set(false);
        this.notifications.error(error);
      }
    });
  }

  /** Lien temporaire vers le blob, cliqué puis révoqué. */
  private saveFile(blob: Blob, fileName: string): void {
    const objectUrl = URL.createObjectURL(blob);
    const link = this.document.createElement('a');
    link.href = objectUrl;
    link.download = fileName;
    link.click();
    setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
  }

  // ---------- Batch ----------

  protected runBatch(): void {
    this.isRunning.set(true);
    this.invoiceService.runBatch().subscribe({
      next: (result) => {
        this.isRunning.set(false);
        this.runResult.set(result);
        this.resultOpen.set(true);
        this.load();
      },
      error: (error: unknown) => {
        this.isRunning.set(false);
        this.notifications.error(error);
      }
    });
  }

  protected goErrors(): void {
    this.router.navigate(['/invoices/errors']);
  }

  protected openErrors(): void {
    const run = this.runResult();
    this.resultOpen.set(false);
    this.router.navigate(['/invoices/errors'], { queryParams: run ? { runId: run.id } : {} });
  }

  // ---------- Export ----------

  protected export(): void {
    this.isExporting.set(true);
    this.exportService.invoices().subscribe({
      next: () => {
        this.isExporting.set(false);
        this.notifications.success('settings.exported');
      },
      error: (error: unknown) => {
        this.isExporting.set(false);
        this.notifications.error(error);
      }
    });
  }
}

import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormsModule,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Button } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { Dialog } from 'primeng/dialog';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { TableLazyLoadEvent } from 'primeng/types/table';
import { Subject, debounceTime } from 'rxjs';
import { formatMoney } from '../../core/format';
import { HasUnsavedChanges } from '../../core/unsaved-changes.guard';
import { Part, StockMovement, StockMovementType } from '../../models/part.model';
import { AuthService } from '../../services/auth.service';
import { LeaveConfirmService } from '../../services/leave-confirm.service';
import { NotificationService } from '../../services/notification.service';
import { PartService } from '../../services/part.service';
import { SettingsService } from '../../services/settings.service';

type DialogMode = 'form' | 'stock' | 'history' | 'confirm';

const MOVEMENT_TYPES: StockMovementType[] = ['IN', 'OUT', 'ADJUSTMENT'];

/**
 * Même règle que le back-end : IN et OUT demandent une quantité d'au moins 1,
 * ADJUSTMENT est une différence signée qui ne peut pas être nulle.
 */
const quantityRule: ValidatorFn = (group) => {
  const type = group.get('type')?.value as StockMovementType | undefined;
  const quantity = group.get('quantity')?.value as number | null | undefined;
  if (quantity === null || quantity === undefined) {
    return null; // « obligatoire » est signalé par le champ lui même
  }
  if (type === 'ADJUSTMENT') {
    return quantity === 0 ? { quantity: true } : null;
  }
  return quantity < 1 ? { quantity: true } : null;
};

@Component({
  selector: 'app-parts',
  imports: [
    DatePipe,
    FormsModule,
    ReactiveFormsModule,
    TranslatePipe,
    TableModule,
    Button,
    Checkbox,
    Dialog,
    IconField,
    InputIcon,
    InputNumber,
    InputText,
    Message,
    Select,
    Tag
  ],
  templateUrl: './parts.html',
  styleUrl: './parts.css'
})
export class Parts implements OnInit, HasUnsavedChanges {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly partService = inject(PartService);
  private readonly notifications = inject(NotificationService);
  private readonly leaveConfirm = inject(LeaveConfirmService);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);
  protected readonly settings = inject(SettingsService);

  // Liste (les données arrivent déjà filtrées et paginées par le back-end)
  protected readonly items = signal<Part[]>([]);
  protected readonly total = signal(0);
  protected readonly first = signal(0);
  protected readonly rows = signal(10);
  protected readonly isLoading = signal(false);
  protected readonly search = signal('');
  protected readonly activeFilter = signal<boolean | null>(null);
  protected readonly lowStockOnly = signal(false);
  private readonly searchInput = new Subject<string>();

  protected readonly statusOptions = computed(() => [
    { value: null, label: this.translate.instant('parts.allStatuses') },
    { value: true, label: this.translate.instant('parts.active') },
    { value: false, label: this.translate.instant('parts.inactive') }
  ]);

  protected readonly movementTypes = MOVEMENT_TYPES;

  // Fenêtre de dialogue (création, modification, stock, journal, confirmation)
  protected readonly dialogOpen = signal(false);
  protected readonly mode = signal<DialogMode>('form');
  // null en création ; la pièce visée dans tous les autres cas
  protected readonly selected = signal<Part | null>(null);
  protected readonly isSaving = signal(false);
  protected readonly formError = signal<string | null>(null);

  // Journal d'une pièce
  protected readonly movements = signal<StockMovement[]>([]);
  protected readonly movementsTotal = signal(0);
  protected readonly movementsFirst = signal(0);
  protected readonly movementsLoading = signal(false);
  protected readonly movementsRows = 10;

  protected readonly partForm = this.fb.group({
    reference: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', [Validators.required, Validators.maxLength(255)]],
    unitPrice: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
    lowStockThreshold: this.fb.control<number | null>(0, [Validators.required, Validators.min(0)]),
    leadTimeDays: this.fb.control<number | null>(0, [Validators.required, Validators.min(0)])
  });

  protected readonly stockForm = this.fb.group(
    {
      type: this.fb.control<StockMovementType>('IN'),
      quantity: this.fb.control<number | null>(null, Validators.required),
      reason: ['', Validators.maxLength(255)]
    },
    { validators: quantityRule }
  );

  ngOnInit(): void {
    // On attend la fin de la frappe avant d'appeler le back-end.
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

  protected onStatusChange(active: boolean | null): void {
    this.activeFilter.set(active);
    this.first.set(0);
    this.load();
  }

  protected onLowStockChange(lowStock: boolean): void {
    this.lowStockOnly.set(lowStock);
    this.first.set(0);
    this.load();
  }

  private load(): void {
    this.isLoading.set(true);
    const page = Math.floor(this.first() / this.rows());
    this.partService
      .search(
        { q: this.search(), active: this.activeFilter(), lowStock: this.lowStockOnly() },
        page,
        this.rows()
      )
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

  // ---------- Ouverture des fenêtres ----------

  protected startCreate(): void {
    this.selected.set(null);
    this.partForm.reset();
    this.formError.set(null);
    this.mode.set('form');
    this.dialogOpen.set(true);
  }

  protected startEdit(part: Part): void {
    this.selected.set(part);
    this.partForm.reset({
      reference: part.reference,
      name: part.name,
      unitPrice: part.unitPrice,
      lowStockThreshold: part.lowStockThreshold,
      leadTimeDays: part.leadTimeDays
    });
    this.formError.set(null);
    this.mode.set('form');
    this.dialogOpen.set(true);
  }

  protected startStock(part: Part): void {
    this.selected.set(part);
    this.stockForm.reset();
    this.formError.set(null);
    this.mode.set('stock');
    this.dialogOpen.set(true);
  }

  protected startHistory(part: Part): void {
    this.selected.set(part);
    this.movements.set([]);
    this.movementsTotal.set(0);
    this.movementsFirst.set(0);
    this.mode.set('history');
    this.dialogOpen.set(true);
    this.loadMovements();
  }

  protected startConfirm(part: Part): void {
    this.selected.set(part);
    this.mode.set('confirm');
    this.dialogOpen.set(true);
  }

  protected async requestClose(): Promise<void> {
    if (this.hasUnsavedChanges() && !(await this.leaveConfirm.confirmLeave())) {
      return;
    }
    this.dialogOpen.set(false);
  }

  /** Utilisé aussi par le guard de sortie de page. */
  hasUnsavedChanges(): boolean {
    if (!this.dialogOpen()) {
      return false;
    }
    if (this.mode() === 'form') {
      return this.partForm.dirty;
    }
    return this.mode() === 'stock' && this.stockForm.dirty;
  }

  // ---------- Actions ----------

  protected savePart(): void {
    this.formError.set(null);
    if (this.partForm.invalid) {
      this.partForm.markAllAsTouched();
      this.formError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    const value = this.partForm.getRawValue();
    const request = {
      reference: value.reference.trim(),
      name: value.name.trim(),
      unitPrice: value.unitPrice ?? 0,
      lowStockThreshold: value.lowStockThreshold ?? 0,
      leadTimeDays: value.leadTimeDays ?? 0
    };
    const current = this.selected();
    const call = current
      ? this.partService.update(current.id, request)
      : this.partService.create(request);

    this.isSaving.set(true);
    this.partForm.disable();
    call.subscribe({
      next: (saved) => {
        this.partForm.enable();
        this.isSaving.set(false);
        this.partForm.markAsPristine();
        this.notifications.success(current ? 'parts.updated' : 'parts.created', { name: saved.name });
        this.dialogOpen.set(false);
        this.load();
      },
      error: (error: unknown) => {
        this.partForm.enable();
        this.isSaving.set(false);
        this.formError.set(this.notifications.errorText(error));
      }
    });
  }

  protected saveStock(): void {
    this.formError.set(null);
    const part = this.selected();
    if (!part) {
      return;
    }
    if (this.stockForm.invalid) {
      this.stockForm.markAllAsTouched();
      this.formError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    const value = this.stockForm.getRawValue();
    this.isSaving.set(true);
    this.stockForm.disable();
    this.partService
      .adjustStock(part.id, {
        type: value.type,
        quantity: value.quantity ?? 0,
        reason: value.reason.trim() ? value.reason.trim() : null
      })
      .subscribe({
        next: (saved) => {
          this.stockForm.enable();
          this.isSaving.set(false);
          this.stockForm.markAsPristine();
          this.notifications.success('parts.stockUpdated', { name: saved.name });
          this.dialogOpen.set(false);
          this.load();
        },
        error: (error: unknown) => {
          this.stockForm.enable();
          this.isSaving.set(false);
          this.formError.set(this.notifications.errorText(error));
        }
      });
  }

  protected confirmToggleActive(): void {
    const part = this.selected();
    if (!part) {
      return;
    }
    const wasActive = part.active;
    this.isSaving.set(true);
    const call = wasActive
      ? this.partService.deactivate(part.id)
      : this.partService.activate(part.id);
    call.subscribe({
      next: (saved) => {
        this.isSaving.set(false);
        this.notifications.success(wasActive ? 'parts.deactivated' : 'parts.activated', {
          name: saved.name
        });
        this.dialogOpen.set(false);
        this.load();
      },
      error: (error: unknown) => {
        this.isSaving.set(false);
        this.dialogOpen.set(false);
        this.notifications.error(error);
      }
    });
  }

  // ---------- Journal ----------

  protected onMovementsLazyLoad(event: TableLazyLoadEvent): void {
    this.movementsFirst.set(event.first ?? 0);
    this.loadMovements();
  }

  private loadMovements(): void {
    const part = this.selected();
    if (!part) {
      return;
    }
    this.movementsLoading.set(true);
    const page = Math.floor(this.movementsFirst() / this.movementsRows);
    this.partService.movements(part.id, page, this.movementsRows).subscribe({
      next: (result) => {
        this.movements.set(result.content);
        this.movementsTotal.set(result.totalElements);
        this.movementsLoading.set(false);
      },
      error: (error: unknown) => {
        this.movementsLoading.set(false);
        this.notifications.error(error);
      }
    });
  }

  /** Signe affiché dans le journal : + pour une entrée, - pour une sortie, la valeur signée pour un ajustement. */
  protected signedQuantity(movement: StockMovement): string {
    if (movement.type === 'OUT') {
      return `-${movement.quantity}`;
    }
    return movement.quantity > 0 ? `+${movement.quantity}` : `${movement.quantity}`;
  }
}

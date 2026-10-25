import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TableLazyLoadEvent } from 'primeng/types/table';
import { Button } from 'primeng/button';
import { Drawer } from 'primeng/drawer';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { Subject, debounceTime } from 'rxjs';
import { HasUnsavedChanges } from '../../core/unsaved-changes.guard';
import { Client, Vehicle } from '../../models/client.model';
import { AuthService } from '../../services/auth.service';
import { ClientService } from '../../services/client.service';
import { LeaveConfirmService } from '../../services/leave-confirm.service';
import { NotificationService } from '../../services/notification.service';

type DrawerMode = 'view' | 'edit' | 'create' | 'addVehicle' | 'confirm';

// Même règle que le back-end pour le téléphone.
const PHONE_PATTERN = /^[0-9+()\-. ]{6,30}$/;

@Component({
  selector: 'app-clients',
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    TableModule,
    Button,
    Drawer,
    IconField,
    InputIcon,
    InputText,
    Message,
    Tag
  ],
  templateUrl: './clients.html',
  styleUrl: './clients.css'
})
export class Clients implements OnInit, HasUnsavedChanges {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly clientService = inject(ClientService);
  private readonly notifications = inject(NotificationService);
  private readonly leaveConfirm = inject(LeaveConfirmService);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  // Liste (les données arrivent déjà filtrées et paginées par le back-end)
  protected readonly items = signal<Client[]>([]);
  protected readonly total = signal(0);
  protected readonly first = signal(0);
  protected readonly rows = signal(10);
  protected readonly isLoading = signal(false);
  protected readonly search = signal('');
  private readonly searchInput = new Subject<string>();

  // Tiroir
  protected readonly drawerOpen = signal(false);
  protected readonly mode = signal<DrawerMode>('view');
  protected readonly selected = signal<Client | null>(null);
  protected readonly vehicles = signal<Vehicle[]>([]);
  protected readonly isSaving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly selectedName = computed(() => {
    const client = this.selected();
    return client ? `${client.firstName} ${client.lastName}` : '';
  });

  protected readonly clientForm = this.fb.group({
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    phone: ['', Validators.pattern(PHONE_PATTERN)]
  });

  protected readonly vehicleForm = this.fb.group({
    plate: ['', [Validators.required, Validators.maxLength(20)]],
    brand: ['', [Validators.required, Validators.maxLength(100)]],
    model: ['', [Validators.required, Validators.maxLength(100)]]
  });

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

  private load(): void {
    this.isLoading.set(true);
    const page = Math.floor(this.first() / this.rows());
    this.clientService.search(this.search(), page, this.rows()).subscribe({
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

  // ---------- Tiroir ----------

  protected open(client: Client): void {
    this.selected.set(client);
    this.mode.set('view');
    this.formError.set(null);
    this.vehicles.set([]);
    this.drawerOpen.set(true);
    this.loadVehicles(client.id);
  }

  protected startCreate(): void {
    this.selected.set(null);
    this.clientForm.reset();
    this.formError.set(null);
    this.mode.set('create');
    this.drawerOpen.set(true);
  }

  protected startEdit(): void {
    const client = this.selected();
    if (!client) {
      return;
    }
    this.clientForm.reset({
      lastName: client.lastName,
      firstName: client.firstName,
      email: client.email,
      phone: client.phone ?? ''
    });
    this.formError.set(null);
    this.mode.set('edit');
  }

  protected startAddVehicle(): void {
    this.vehicleForm.reset();
    this.formError.set(null);
    this.mode.set('addVehicle');
  }

  protected startConfirm(): void {
    this.mode.set('confirm');
  }

  /** Annuler dans un formulaire : on demande confirmation seulement si quelque chose a été saisi. */
  protected async cancelForm(): Promise<void> {
    if (this.hasUnsavedChanges() && !(await this.leaveConfirm.confirmLeave())) {
      return;
    }
    if (this.mode() === 'create') {
      this.closeDrawer();
    } else {
      this.mode.set('view');
    }
  }

  protected async requestClose(): Promise<void> {
    if (this.hasUnsavedChanges() && !(await this.leaveConfirm.confirmLeave())) {
      return;
    }
    this.closeDrawer();
  }

  /** Utilisé aussi par le guard de sortie de page. */
  hasUnsavedChanges(): boolean {
    if (!this.drawerOpen()) {
      return false;
    }
    switch (this.mode()) {
      case 'edit':
      case 'create':
        return this.clientForm.dirty;
      case 'addVehicle':
        return this.vehicleForm.dirty;
      default:
        return false;
    }
  }

  private closeDrawer(): void {
    this.drawerOpen.set(false);
    this.mode.set('view');
    this.selected.set(null);
  }

  private loadVehicles(clientId: string): void {
    this.clientService.getVehicles(clientId).subscribe({
      next: (list) => this.vehicles.set(list),
      error: (error: unknown) => this.notifications.error(error)
    });
  }

  // ---------- Actions ----------

  protected saveClient(): void {
    this.formError.set(null);
    if (this.clientForm.invalid) {
      this.clientForm.markAllAsTouched();
      this.formError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    const value = this.clientForm.getRawValue();
    const request = { ...value, phone: value.phone.trim() ? value.phone.trim() : null };
    const current = this.selected();

    this.isSaving.set(true);
    this.clientForm.disable();
    const call =
      this.mode() === 'create'
        ? this.clientService.create(request)
        : this.clientService.update((current as Client).id, request);
    const wasCreate = this.mode() === 'create';

    call.subscribe({
      next: (saved) => {
        this.endSaving(this.clientForm);
        const name = `${saved.firstName} ${saved.lastName}`;
        this.notifications.success(wasCreate ? 'clients.created' : 'clients.updated', { name });
        this.load();
        if (wasCreate) {
          this.closeDrawer();
        } else {
          this.selected.set(saved);
          this.clientForm.markAsPristine();
          this.mode.set('view');
        }
      },
      error: (error: unknown) => {
        this.endSaving(this.clientForm);
        this.formError.set(this.notifications.errorText(error));
      }
    });
  }

  protected saveVehicle(): void {
    this.formError.set(null);
    const client = this.selected();
    if (this.vehicleForm.invalid || !client) {
      this.vehicleForm.markAllAsTouched();
      this.formError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    this.isSaving.set(true);
    this.vehicleForm.disable();
    this.clientService
      .createVehicle({ clientId: client.id, ...this.vehicleForm.getRawValue() })
      .subscribe({
        next: (vehicle) => {
          this.endSaving(this.vehicleForm);
          this.vehicleForm.markAsPristine();
          this.notifications.success('clients.vehicleCreated', {
            name: `${vehicle.brand} ${vehicle.model}`
          });
          this.loadVehicles(client.id);
          this.mode.set('view');
        },
        error: (error: unknown) => {
          this.endSaving(this.vehicleForm);
          this.formError.set(this.notifications.errorText(error));
        }
      });
  }

  /** Désactivation ou réactivation (ADMIN), après confirmation dans le tiroir. */
  protected confirmToggleActive(): void {
    const client = this.selected();
    if (!client) {
      return;
    }
    this.isSaving.set(true);
    const wasActive = client.active;
    const call = wasActive
      ? this.clientService.deactivate(client.id)
      : this.clientService.activate(client.id);
    call.subscribe({
      next: (saved) => {
        this.isSaving.set(false);
        this.notifications.success(wasActive ? 'clients.deactivated' : 'clients.activated', {
          name: `${saved.firstName} ${saved.lastName}`
        });
        this.selected.set(saved);
        this.mode.set('view');
        this.load();
      },
      error: (error: unknown) => {
        this.isSaving.set(false);
        this.mode.set('view');
        this.notifications.error(error);
      }
    });
  }

  private endSaving(form: { enable: () => void }): void {
    form.enable();
    this.isSaving.set(false);
  }
}

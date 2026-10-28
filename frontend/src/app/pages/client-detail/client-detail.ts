import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Skeleton } from 'primeng/skeleton';
import { Tag } from 'primeng/tag';
import { HasUnsavedChanges } from '../../core/unsaved-changes.guard';
import { Client } from '../../models/client.model';
import { Vehicle } from '../../models/vehicle.model';
import { AuthService } from '../../services/auth.service';
import { ClientService } from '../../services/client.service';
import { LeaveConfirmService } from '../../services/leave-confirm.service';
import { NotificationService } from '../../services/notification.service';
import { VehicleService } from '../../services/vehicle.service';
import { AppointmentHistory } from '../../shared/appointment-history';
import { DetailHeader } from '../../shared/detail-header';

type DialogMode = 'edit' | 'addVehicle' | 'confirm';

// Même règle que le back-end pour le téléphone.
const PHONE_PATTERN = /^[0-9+()\-. ]{6,30}$/;

/** Page détail d'un client : identité, véhicules et historique des prestations. */
@Component({
  selector: 'app-client-detail',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    Button,
    Dialog,
    InputText,
    Message,
    Skeleton,
    Tag,
    AppointmentHistory,
    DetailHeader
  ],
  templateUrl: './client-detail.html',
  styleUrl: './client-detail.css'
})
export class ClientDetail implements OnInit, HasUnsavedChanges {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly clientService = inject(ClientService);
  private readonly vehicleService = inject(VehicleService);
  private readonly notifications = inject(NotificationService);
  private readonly leaveConfirm = inject(LeaveConfirmService);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  protected readonly client = signal<Client | null>(null);
  protected readonly vehicles = signal<Vehicle[]>([]);
  protected readonly vehiclesLoading = signal(false);
  protected readonly isLoading = signal(true);
  protected readonly loadFailed = signal(false);
  protected readonly name = computed(() => {
    const c = this.client();
    return c ? `${c.firstName} ${c.lastName}` : '';
  });

  // Fenêtre de dialogue
  protected readonly dialogOpen = signal(false);
  protected readonly mode = signal<DialogMode>('edit');
  protected readonly isSaving = signal(false);
  protected readonly formError = signal<string | null>(null);

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

  private clientId = '';

  /** Source du tableau des prestations : relue à chaque changement de page. */
  protected readonly appointmentsSource = (page: number, size: number) =>
    this.clientService.getAppointments(this.clientId, page, size);

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.clientId = params.get('id') ?? '';
      this.client.set(null);
      this.vehicles.set([]);
      this.load();
    });
  }

  protected load(): void {
    this.isLoading.set(true);
    this.loadFailed.set(false);
    this.clientService.getById(this.clientId).subscribe({
      next: (client) => {
        this.client.set(client);
        this.isLoading.set(false);
        this.loadVehicles();
      },
      error: (error: unknown) => {
        this.isLoading.set(false);
        this.loadFailed.set(true);
        this.notifications.error(error);
      }
    });
  }

  private loadVehicles(): void {
    this.vehiclesLoading.set(true);
    this.clientService.getVehicles(this.clientId).subscribe({
      next: (list) => {
        this.vehicles.set(list);
        this.vehiclesLoading.set(false);
      },
      error: (error: unknown) => {
        this.vehiclesLoading.set(false);
        this.notifications.error(error);
      }
    });
  }

  // ---------- Fenêtre de dialogue ----------

  protected startEdit(): void {
    const client = this.client();
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
    this.dialogOpen.set(true);
  }

  protected startAddVehicle(): void {
    this.vehicleForm.reset();
    this.formError.set(null);
    this.mode.set('addVehicle');
    this.dialogOpen.set(true);
  }

  protected startConfirm(): void {
    this.mode.set('confirm');
    this.dialogOpen.set(true);
  }

  /** Utilisé aussi par le guard de sortie de page. */
  hasUnsavedChanges(): boolean {
    if (!this.dialogOpen()) {
      return false;
    }
    switch (this.mode()) {
      case 'edit':
        return this.clientForm.dirty;
      case 'addVehicle':
        return this.vehicleForm.dirty;
      default:
        return false;
    }
  }

  protected async requestClose(): Promise<void> {
    if (this.hasUnsavedChanges() && !(await this.leaveConfirm.confirmLeave())) {
      return;
    }
    this.dialogOpen.set(false);
  }

  // ---------- Actions ----------

  protected saveClient(): void {
    this.formError.set(null);
    const current = this.client();
    if (this.clientForm.invalid || !current) {
      this.clientForm.markAllAsTouched();
      this.formError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    const value = this.clientForm.getRawValue();
    const request = { ...value, phone: value.phone.trim() ? value.phone.trim() : null };

    this.isSaving.set(true);
    this.clientForm.disable();
    this.clientService.update(current.id, request).subscribe({
      next: (saved) => {
        this.clientForm.enable();
        this.isSaving.set(false);
        this.clientForm.markAsPristine();
        this.client.set(saved);
        this.notifications.success('clients.updated', { name: `${saved.firstName} ${saved.lastName}` });
        this.dialogOpen.set(false);
      },
      error: (error: unknown) => {
        this.clientForm.enable();
        this.isSaving.set(false);
        this.formError.set(this.notifications.errorText(error));
      }
    });
  }

  protected saveVehicle(): void {
    this.formError.set(null);
    const client = this.client();
    if (this.vehicleForm.invalid || !client) {
      this.vehicleForm.markAllAsTouched();
      this.formError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    this.isSaving.set(true);
    this.vehicleForm.disable();
    this.vehicleService.create({ clientId: client.id, ...this.vehicleForm.getRawValue() }).subscribe({
      next: (vehicle) => {
        this.vehicleForm.enable();
        this.isSaving.set(false);
        this.vehicleForm.markAsPristine();
        this.notifications.success('clients.vehicleCreated', { name: `${vehicle.brand} ${vehicle.model}` });
        this.loadVehicles();
        this.dialogOpen.set(false);
      },
      error: (error: unknown) => {
        this.vehicleForm.enable();
        this.isSaving.set(false);
        this.formError.set(this.notifications.errorText(error));
      }
    });
  }

  /** Désactivation ou réactivation (ADMIN), après confirmation dans la fenêtre. */
  protected confirmToggleActive(): void {
    const client = this.client();
    if (!client) {
      return;
    }
    this.isSaving.set(true);
    const wasActive = client.active;
    const call = wasActive ? this.clientService.deactivate(client.id) : this.clientService.activate(client.id);
    call.subscribe({
      next: (saved) => {
        this.isSaving.set(false);
        this.notifications.success(wasActive ? 'clients.deactivated' : 'clients.activated', {
          name: `${saved.firstName} ${saved.lastName}`
        });
        this.client.set(saved);
        this.dialogOpen.set(false);
      },
      error: (error: unknown) => {
        this.isSaving.set(false);
        this.dialogOpen.set(false);
        this.notifications.error(error);
      }
    });
  }
}

import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AutoComplete, AutoCompleteCompleteEvent } from 'primeng/autocomplete';
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

type DialogMode = 'edit' | 'confirm';
type ClientOption = Pick<Client, 'id' | 'firstName' | 'lastName'>;

/** Page détail d'un véhicule : identité, client (lien) et historique des rendez-vous. */
@Component({
  selector: 'app-vehicle-detail',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    AutoComplete,
    Button,
    Dialog,
    InputText,
    Message,
    Skeleton,
    Tag,
    AppointmentHistory,
    DetailHeader
  ],
  templateUrl: './vehicle-detail.html',
  styleUrl: './vehicle-detail.css'
})
export class VehicleDetail implements OnInit, HasUnsavedChanges {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly vehicleService = inject(VehicleService);
  private readonly clientService = inject(ClientService);
  private readonly notifications = inject(NotificationService);
  private readonly leaveConfirm = inject(LeaveConfirmService);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  protected readonly vehicle = signal<Vehicle | null>(null);
  protected readonly isLoading = signal(true);
  protected readonly loadFailed = signal(false);
  protected readonly label = computed(() => {
    const v = this.vehicle();
    return v ? `${v.brand} ${v.model}` : '';
  });

  // Fenêtre de dialogue
  protected readonly dialogOpen = signal(false);
  protected readonly mode = signal<DialogMode>('edit');
  protected readonly isSaving = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly clientSuggestions = signal<ClientOption[]>([]);
  protected readonly vehicleForm = this.fb.group({
    client: this.fb.control<ClientOption | null>(null, Validators.required),
    plate: ['', [Validators.required, Validators.maxLength(20)]],
    brand: ['', [Validators.required, Validators.maxLength(100)]],
    model: ['', [Validators.required, Validators.maxLength(100)]]
  });
  protected readonly clientLabel = (client: ClientOption): string => `${client.firstName} ${client.lastName}`;

  private vehicleId = '';

  protected readonly appointmentsSource = (page: number, size: number) =>
    this.vehicleService.getAppointments(this.vehicleId, page, size);

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.vehicleId = params.get('id') ?? '';
      this.vehicle.set(null);
      this.load();
    });
  }

  protected load(): void {
    this.isLoading.set(true);
    this.loadFailed.set(false);
    this.vehicleService.getById(this.vehicleId).subscribe({
      next: (vehicle) => {
        this.vehicle.set(vehicle);
        this.isLoading.set(false);
      },
      error: (error: unknown) => {
        this.isLoading.set(false);
        this.loadFailed.set(true);
        this.notifications.error(error);
      }
    });
  }

  // ---------- Fenêtre de dialogue ----------

  protected startEdit(): void {
    const v = this.vehicle();
    if (!v) {
      return;
    }
    this.vehicleForm.reset({
      client: { id: v.clientId, firstName: v.clientFirstName, lastName: v.clientLastName },
      plate: v.plate,
      brand: v.brand,
      model: v.model
    });
    this.formError.set(null);
    this.mode.set('edit');
    this.dialogOpen.set(true);
  }

  protected startConfirm(): void {
    this.mode.set('confirm');
    this.dialogOpen.set(true);
  }

  /** Utilisé aussi par le guard de sortie de page. */
  hasUnsavedChanges(): boolean {
    return this.dialogOpen() && this.mode() === 'edit' && this.vehicleForm.dirty;
  }

  protected async requestClose(): Promise<void> {
    if (this.hasUnsavedChanges() && !(await this.leaveConfirm.confirmLeave())) {
      return;
    }
    this.dialogOpen.set(false);
  }

  /** Les clients actifs sont cherchés côté back-end à chaque saisie. */
  protected searchClients(event: AutoCompleteCompleteEvent): void {
    this.clientService.search(event.query, 0, 25, true).subscribe({
      next: (page) => this.clientSuggestions.set(page.content),
      error: (error: unknown) => this.notifications.error(error)
    });
  }

  // ---------- Actions ----------

  protected save(): void {
    this.formError.set(null);
    const current = this.vehicle();
    if (this.vehicleForm.invalid || !current) {
      this.vehicleForm.markAllAsTouched();
      this.formError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    const value = this.vehicleForm.getRawValue();
    const request = {
      clientId: (value.client as ClientOption).id,
      plate: value.plate.trim(),
      brand: value.brand.trim(),
      model: value.model.trim()
    };
    this.isSaving.set(true);
    this.vehicleForm.disable();
    this.vehicleService.update(current.id, request).subscribe({
      next: (saved) => {
        this.vehicleForm.enable();
        this.isSaving.set(false);
        this.vehicleForm.markAsPristine();
        this.notifications.success('vehicles.updated', { name: `${saved.brand} ${saved.model}` });
        this.vehicle.set(saved);
        this.dialogOpen.set(false);
      },
      error: (error: unknown) => {
        this.vehicleForm.enable();
        this.isSaving.set(false);
        this.formError.set(this.notifications.errorText(error));
      }
    });
  }

  /** Désactivation (« Supprimer ») ou réactivation, après confirmation dans la fenêtre. */
  protected confirmToggleActive(): void {
    const vehicle = this.vehicle();
    if (!vehicle) {
      return;
    }
    this.isSaving.set(true);
    const wasActive = vehicle.active;
    const name = `${vehicle.brand} ${vehicle.model}`;
    const onSuccess = (saved: Vehicle): void => {
      this.isSaving.set(false);
      this.notifications.success(wasActive ? 'vehicles.deleted' : 'vehicles.reactivated', { name });
      this.vehicle.set(saved);
      this.dialogOpen.set(false);
    };
    const onError = (error: unknown): void => {
      // Ex. rendez-vous en cours ou planifié : le back-end explique le refus.
      this.isSaving.set(false);
      this.dialogOpen.set(false);
      this.notifications.error(error);
    };

    if (wasActive) {
      // La désactivation répond 204 sans corps : on met l'état local à jour.
      this.vehicleService.deactivate(vehicle.id).subscribe({
        next: () => onSuccess({ ...vehicle, active: false }),
        error: onError
      });
    } else {
      this.vehicleService.reactivate(vehicle.id).subscribe({ next: onSuccess, error: onError });
    }
  }
}

import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TableLazyLoadEvent } from 'primeng/types/table';
import { AutoComplete, AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { Button } from 'primeng/button';
import { DatePicker } from 'primeng/datepicker';
import { Drawer } from 'primeng/drawer';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { Textarea } from 'primeng/textarea';
import { Subject, debounceTime } from 'rxjs';
import { HasUnsavedChanges } from '../../core/unsaved-changes.guard';
import { Appointment, AppointmentStatus } from '../../models/appointment.model';
import { Client } from '../../models/client.model';
import { AppointmentService } from '../../services/appointment.service';
import { AuthService } from '../../services/auth.service';
import { ClientService } from '../../services/client.service';
import { LeaveConfirmService } from '../../services/leave-confirm.service';
import { NotificationService } from '../../services/notification.service';
import { SettingsService } from '../../services/settings.service';

type DrawerMode = 'view' | 'edit' | 'create' | 'confirm';
type StatusAction = 'CANCELLED' | 'COMPLETED';
type ClientOption = Pick<Client, 'id' | 'firstName' | 'lastName'>;

const STATUSES: AppointmentStatus[] = ['PLANNED', 'COMPLETED', 'CANCELLED'];

/** Jour local au format AAAA-MM-JJ, attendu par le back-end pour from et to. */
function toIsoDay(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

@Component({
  selector: 'app-appointments',
  imports: [
    DatePipe,
    FormsModule,
    ReactiveFormsModule,
    TranslatePipe,
    TableModule,
    AutoComplete,
    Button,
    DatePicker,
    Drawer,
    IconField,
    InputIcon,
    InputText,
    Message,
    Select,
    Tag,
    Textarea
  ],
  templateUrl: './appointments.html',
  styleUrl: './appointments.css'
})
export class Appointments implements OnInit, HasUnsavedChanges {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly appointmentService = inject(AppointmentService);
  private readonly clientService = inject(ClientService);
  private readonly notifications = inject(NotificationService);
  private readonly leaveConfirm = inject(LeaveConfirmService);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);
  protected readonly settings = inject(SettingsService);

  protected readonly statuses = STATUSES;
  protected readonly now = new Date();

  // Un compte CLIENT sans fiche client ne voit rien : on affiche un message à la place de la table.
  protected readonly isUnlinkedClient = computed(() => {
    const user = this.auth.user();
    return user?.role === 'CLIENT' && !user.clientLinked;
  });

  // Liste
  protected readonly items = signal<Appointment[]>([]);
  protected readonly total = signal(0);
  protected readonly first = signal(0);
  protected readonly rows = signal(10);
  protected readonly isLoading = signal(false);
  protected readonly search = signal('');
  protected readonly statusFilter = signal<AppointmentStatus | null>(null);
  protected readonly fromFilter = signal<Date | null>(null);
  protected readonly toFilter = signal<Date | null>(null);
  private readonly searchInput = new Subject<string>();

  // Tiroir
  protected readonly drawerOpen = signal(false);
  protected readonly mode = signal<DrawerMode>('view');
  protected readonly selected = signal<Appointment | null>(null);
  protected readonly statusAction = signal<StatusAction>('CANCELLED');
  protected readonly isSaving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly selectedName = computed(() => {
    const a = this.selected();
    return a ? `${a.clientFirstName} ${a.clientLastName}` : '';
  });
  protected readonly canChange = computed(
    () => this.auth.canWrite() && this.selected()?.status === 'PLANNED'
  );

  // Formulaire
  protected readonly clientSuggestions = signal<ClientOption[]>([]);
  protected readonly vehicleOptions = signal<{ id: string; label: string }[]>([]);
  protected readonly appointmentForm = this.fb.group({
    client: this.fb.control<ClientOption | null>(null, Validators.required),
    vehicleId: ['', Validators.required],
    date: this.fb.control<Date | null>(null, Validators.required),
    reason: ['', [Validators.required, Validators.maxLength(500)]]
  });

  protected readonly clientLabel = (client: ClientOption): string =>
    `${client.firstName} ${client.lastName}`;

  ngOnInit(): void {
    this.searchInput
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe((text) => {
        this.search.set(text);
        this.reloadFromStart();
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

  protected onStatusChange(status: AppointmentStatus | null): void {
    this.statusFilter.set(status);
    this.reloadFromStart();
  }

  protected onFromChange(date: Date | null): void {
    this.fromFilter.set(date);
    this.reloadFromStart();
  }

  protected onToChange(date: Date | null): void {
    this.toFilter.set(date);
    this.reloadFromStart();
  }

  private reloadFromStart(): void {
    this.first.set(0);
    this.load();
  }

  private load(): void {
    if (this.isUnlinkedClient()) {
      return;
    }
    this.isLoading.set(true);
    const page = Math.floor(this.first() / this.rows());
    const filters = {
      q: this.search(),
      status: this.statusFilter(),
      from: this.fromFilter() ? toIsoDay(this.fromFilter() as Date) : null,
      to: this.toFilter() ? toIsoDay(this.toFilter() as Date) : null
    };
    this.appointmentService.search(filters, page, this.rows()).subscribe({
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

  protected open(appointment: Appointment): void {
    this.selected.set(appointment);
    this.mode.set('view');
    this.formError.set(null);
    this.drawerOpen.set(true);
  }

  protected startCreate(): void {
    this.selected.set(null);
    this.appointmentForm.reset();
    this.vehicleOptions.set([]);
    this.formError.set(null);
    this.mode.set('create');
    this.drawerOpen.set(true);
  }

  protected startEdit(): void {
    const a = this.selected();
    if (!a) {
      return;
    }
    this.appointmentForm.reset({
      client: { id: a.clientId, firstName: a.clientFirstName, lastName: a.clientLastName },
      vehicleId: a.vehicleId,
      date: new Date(a.date),
      reason: a.reason
    });
    this.loadVehicles(a.clientId);
    this.formError.set(null);
    this.mode.set('edit');
  }

  protected startConfirm(action: StatusAction): void {
    this.statusAction.set(action);
    this.mode.set('confirm');
  }

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

  hasUnsavedChanges(): boolean {
    const editing = this.mode() === 'edit' || this.mode() === 'create';
    return this.drawerOpen() && editing && this.appointmentForm.dirty;
  }

  private closeDrawer(): void {
    this.drawerOpen.set(false);
    this.mode.set('view');
    this.selected.set(null);
  }

  // ---------- Formulaire : client et véhicule ----------

  /** Les clients actifs sont cherchés côté back-end à chaque saisie. */
  protected searchClients(event: AutoCompleteCompleteEvent): void {
    this.clientService.search(event.query, 0, 25, true).subscribe({
      next: (page) => this.clientSuggestions.set(page.content),
      error: (error: unknown) => this.notifications.error(error)
    });
  }

  protected onClientSelected(client: ClientOption): void {
    // Un autre client implique d'autres véhicules.
    this.appointmentForm.controls.vehicleId.reset('');
    this.loadVehicles(client.id);
  }

  private loadVehicles(clientId: string): void {
    this.clientService.getVehicles(clientId).subscribe({
      next: (list) =>
        this.vehicleOptions.set(
          list.map((v) => ({ id: v.id, label: `${v.brand} ${v.model}, ${v.plate}` }))
        ),
      error: (error: unknown) => this.notifications.error(error)
    });
  }

  // ---------- Actions ----------

  protected save(): void {
    this.formError.set(null);
    if (this.appointmentForm.invalid) {
      this.appointmentForm.markAllAsTouched();
      this.formError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    const value = this.appointmentForm.getRawValue();
    const request = {
      clientId: (value.client as ClientOption).id,
      vehicleId: value.vehicleId,
      date: (value.date as Date).toISOString(),
      reason: value.reason.trim()
    };
    const wasCreate = this.mode() === 'create';

    this.isSaving.set(true);
    this.appointmentForm.disable();
    const call = wasCreate
      ? this.appointmentService.create(request)
      : this.appointmentService.update((this.selected() as Appointment).id, request);

    call.subscribe({
      next: (saved) => {
        this.appointmentForm.enable();
        this.isSaving.set(false);
        this.notifications.success(wasCreate ? 'appointments.created' : 'appointments.updated', {
          name: `${saved.clientFirstName} ${saved.clientLastName}`
        });
        this.load();
        if (wasCreate) {
          this.closeDrawer();
        } else {
          this.appointmentForm.markAsPristine();
          this.selected.set(saved);
          this.mode.set('view');
        }
      },
      error: (error: unknown) => {
        this.appointmentForm.enable();
        this.isSaving.set(false);
        this.formError.set(this.notifications.errorText(error));
      }
    });
  }

  /** Annulation ou fin du rendez-vous, après confirmation dans le tiroir. */
  protected confirmStatusChange(): void {
    const appointment = this.selected();
    if (!appointment) {
      return;
    }
    const action = this.statusAction();
    this.isSaving.set(true);
    this.appointmentService.changeStatus(appointment.id, action).subscribe({
      next: (saved) => {
        this.isSaving.set(false);
        this.notifications.success(
          action === 'CANCELLED' ? 'appointments.cancelled' : 'appointments.completed',
          { name: `${saved.clientFirstName} ${saved.clientLastName}` }
        );
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

  protected statusSeverity(status: AppointmentStatus): 'info' | 'success' | 'secondary' {
    switch (status) {
      case 'PLANNED':
        return 'info';
      case 'COMPLETED':
        return 'success';
      default:
        return 'secondary';
    }
  }
}

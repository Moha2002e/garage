import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TableLazyLoadEvent } from 'primeng/types/table';
import { AutoComplete, AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { Subject, debounceTime } from 'rxjs';
import { HasUnsavedChanges } from '../../core/unsaved-changes.guard';
import { Client } from '../../models/client.model';
import { Vehicle } from '../../models/vehicle.model';
import { AuthService } from '../../services/auth.service';
import { ClientService } from '../../services/client.service';
import { LeaveConfirmService } from '../../services/leave-confirm.service';
import { NotificationService } from '../../services/notification.service';
import { VehicleService } from '../../services/vehicle.service';

type ClientOption = Pick<Client, 'id' | 'firstName' | 'lastName'>;

@Component({
  selector: 'app-vehicles',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    TranslatePipe,
    TableModule,
    AutoComplete,
    Button,
    Dialog,
    IconField,
    InputIcon,
    InputText,
    Message,
    Select,
    Tag
  ],
  templateUrl: './vehicles.html',
  styleUrl: './vehicles.css'
})
export class Vehicles implements OnInit, HasUnsavedChanges {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly vehicleService = inject(VehicleService);
  private readonly clientService = inject(ClientService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly leaveConfirm = inject(LeaveConfirmService);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  // Liste (les données arrivent déjà filtrées et paginées par le back-end)
  protected readonly items = signal<Vehicle[]>([]);
  protected readonly total = signal(0);
  protected readonly first = signal(0);
  protected readonly rows = signal(10);
  protected readonly isLoading = signal(false);
  protected readonly search = signal('');
  protected readonly activeFilter = signal<boolean | null>(null);
  protected readonly activeOptions = [true, false];
  private readonly searchInput = new Subject<string>();

  // Fenêtre de dialogue
  protected readonly dialogOpen = signal(false);
  protected readonly isSaving = signal(false);
  protected readonly formError = signal<string | null>(null);
  // Formulaire
  protected readonly clientSuggestions = signal<ClientOption[]>([]);
  protected readonly vehicleForm = this.fb.group({
    client: this.fb.control<ClientOption | null>(null, Validators.required),
    plate: ['', [Validators.required, Validators.maxLength(20)]],
    brand: ['', [Validators.required, Validators.maxLength(100)]],
    model: ['', [Validators.required, Validators.maxLength(100)]]
  });

  protected readonly clientLabel = (client: ClientOption): string =>
    `${client.firstName} ${client.lastName}`;

  ngOnInit(): void {
    // On attend la fin de la frappe avant d'appeler le back-end.
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

  protected onActiveChange(active: boolean | null): void {
    this.activeFilter.set(active);
    this.reloadFromStart();
  }

  private reloadFromStart(): void {
    this.first.set(0);
    this.load();
  }

  private load(): void {
    this.isLoading.set(true);
    const page = Math.floor(this.first() / this.rows());
    this.vehicleService.search(this.search(), this.activeFilter(), page, this.rows()).subscribe({
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

  // ---------- Fenêtre de dialogue (création) ----------

  /** Une ligne ou le bouton Détail ouvre la page du véhicule. */
  protected open(vehicle: Vehicle): void {
    this.router.navigate(['/vehicles', vehicle.id]);
  }

  protected startCreate(): void {
    this.vehicleForm.reset();
    this.formError.set(null);
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
    return this.dialogOpen() && this.vehicleForm.dirty;
  }

  // ---------- Formulaire : client ----------

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
    if (this.vehicleForm.invalid) {
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
    this.vehicleService.create(request).subscribe({
      next: (saved) => {
        this.vehicleForm.enable();
        this.isSaving.set(false);
        this.vehicleForm.markAsPristine();
        this.notifications.success('vehicles.created', { name: `${saved.brand} ${saved.model}` });
        this.dialogOpen.set(false);
        this.router.navigate(['/vehicles', saved.id]);
      },
      error: (error: unknown) => {
        this.vehicleForm.enable();
        this.isSaving.set(false);
        this.formError.set(this.notifications.errorText(error));
      }
    });
  }
}

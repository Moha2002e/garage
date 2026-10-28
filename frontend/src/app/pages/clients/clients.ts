import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TableLazyLoadEvent } from 'primeng/types/table';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { Subject, debounceTime } from 'rxjs';
import { HasUnsavedChanges } from '../../core/unsaved-changes.guard';
import { Client } from '../../models/client.model';
import { AuthService } from '../../services/auth.service';
import { ClientService } from '../../services/client.service';
import { LeaveConfirmService } from '../../services/leave-confirm.service';
import { NotificationService } from '../../services/notification.service';

// Même règle que le back-end pour le téléphone.
const PHONE_PATTERN = /^[0-9+()\-. ]{6,30}$/;

@Component({
  selector: 'app-clients',
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    TableModule,
    Button,
    Dialog,
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
  private readonly router = inject(Router);
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

  // Fenêtre de création
  protected readonly dialogOpen = signal(false);
  protected readonly isSaving = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly clientForm = this.fb.group({
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    phone: ['', Validators.pattern(PHONE_PATTERN)]
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

  // ---------- Fenêtre de dialogue (création) ----------

  /** Une ligne ou le bouton Détail ouvre la page du client. */
  protected open(client: Client): void {
    this.router.navigate(['/clients', client.id]);
  }

  protected startCreate(): void {
    this.clientForm.reset();
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
    return this.dialogOpen() && this.clientForm.dirty;
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

    this.isSaving.set(true);
    this.clientForm.disable();
    this.clientService.create(request).subscribe({
      next: (saved) => {
        this.clientForm.enable();
        this.isSaving.set(false);
        this.clientForm.markAsPristine();
        this.notifications.success('clients.created', { name: `${saved.firstName} ${saved.lastName}` });
        this.dialogOpen.set(false);
        // On arrive directement sur la fiche du nouveau client.
        this.router.navigate(['/clients', saved.id]);
      },
      error: (error: unknown) => {
        this.clientForm.enable();
        this.isSaving.set(false);
        this.formError.set(this.notifications.errorText(error));
      }
    });
  }
}

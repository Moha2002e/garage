import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { TableLazyLoadEvent } from 'primeng/types/table';
import { AutoComplete, AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { Button } from 'primeng/button';
import { Drawer } from 'primeng/drawer';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { Observable, Subject, concat, debounceTime, last } from 'rxjs';
import { HasUnsavedChanges } from '../../core/unsaved-changes.guard';
import { Role } from '../../models/auth.model';
import { Client } from '../../models/client.model';
import { UserAccount } from '../../models/user.model';
import { AuthService } from '../../services/auth.service';
import { ClientService } from '../../services/client.service';
import { LeaveConfirmService } from '../../services/leave-confirm.service';
import { NotificationService } from '../../services/notification.service';
import { UserService } from '../../services/user.service';

type ClientOption = Pick<Client, 'id' | 'firstName' | 'lastName'>;

const ROLES: Role[] = ['ADMIN', 'EMPLOYEE', 'CLIENT'];

@Component({
  selector: 'app-users',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    TranslatePipe,
    TableModule,
    AutoComplete,
    Button,
    Drawer,
    IconField,
    InputIcon,
    InputText,
    Message,
    Select,
    Tag
  ],
  templateUrl: './users.html',
  styleUrl: './users.css'
})
export class Users implements OnInit, HasUnsavedChanges {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly userService = inject(UserService);
  private readonly clientService = inject(ClientService);
  private readonly notifications = inject(NotificationService);
  private readonly leaveConfirm = inject(LeaveConfirmService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  protected readonly roles = ROLES;

  // Liste
  protected readonly items = signal<UserAccount[]>([]);
  protected readonly total = signal(0);
  protected readonly first = signal(0);
  protected readonly rows = signal(10);
  protected readonly isLoading = signal(false);
  protected readonly search = signal('');
  protected readonly roleFilter = signal<Role | null>(null);
  private readonly searchInput = new Subject<string>();

  // Tiroir
  protected readonly drawerOpen = signal(false);
  protected readonly isEditing = signal(false);
  protected readonly selected = signal<UserAccount | null>(null);
  protected readonly linkedClientName = signal('');
  protected readonly isSaving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly selectedName = computed(() => {
    const u = this.selected();
    return u ? `${u.firstName} ${u.lastName}` : '';
  });

  protected readonly userForm = this.fb.group({
    role: this.fb.control<Role>('CLIENT'),
    client: this.fb.control<ClientOption | null>(null)
  });
  protected readonly clientSuggestions = signal<ClientOption[]>([]);
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

  protected onRoleFilterChange(role: Role | null): void {
    this.roleFilter.set(role);
    this.reloadFromStart();
  }

  private reloadFromStart(): void {
    this.first.set(0);
    this.load();
  }

  private load(): void {
    this.isLoading.set(true);
    const page = Math.floor(this.first() / this.rows());
    this.userService.search(this.search(), this.roleFilter(), page, this.rows()).subscribe({
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

  protected open(user: UserAccount): void {
    this.selected.set(user);
    this.isEditing.set(false);
    this.formError.set(null);
    this.drawerOpen.set(true);
    this.loadLinkedClientName(user);
  }

  /** Le compte ne porte que l'identifiant de la fiche client : on récupère le nom pour l'afficher. */
  private loadLinkedClientName(user: UserAccount): void {
    this.linkedClientName.set('');
    if (!user.clientId) {
      return;
    }
    this.clientService.getById(user.clientId).subscribe({
      next: (client) => this.linkedClientName.set(this.clientLabel(client)),
      error: (error: unknown) => this.notifications.error(error)
    });
  }

  protected startEdit(): void {
    const user = this.selected();
    if (!user) {
      return;
    }
    const names = this.linkedClientName().split(' ');
    this.userForm.reset({
      role: user.role,
      client: user.clientId
        ? { id: user.clientId, firstName: names[0] ?? '', lastName: names.slice(1).join(' ') }
        : null
    });
    this.formError.set(null);
    this.isEditing.set(true);
  }

  protected async cancelEdit(): Promise<void> {
    if (this.hasUnsavedChanges() && !(await this.leaveConfirm.confirmLeave())) {
      return;
    }
    this.isEditing.set(false);
  }

  protected async requestClose(): Promise<void> {
    if (this.hasUnsavedChanges() && !(await this.leaveConfirm.confirmLeave())) {
      return;
    }
    this.drawerOpen.set(false);
    this.isEditing.set(false);
    this.selected.set(null);
  }

  hasUnsavedChanges(): boolean {
    return this.drawerOpen() && this.isEditing() && this.userForm.dirty;
  }

  protected searchClients(event: AutoCompleteCompleteEvent): void {
    this.clientService.search(event.query, 0, 25, true).subscribe({
      next: (page) => this.clientSuggestions.set(page.content),
      error: (error: unknown) => this.notifications.error(error)
    });
  }

  // ---------- Enregistrement ----------

  protected save(): void {
    const user = this.selected();
    if (!user) {
      return;
    }
    const value = this.userForm.getRawValue();
    const calls: Observable<UserAccount>[] = [];
    // Seul l'ADMIN change un rôle (contrôle aussi fait par le back-end).
    if (this.auth.isAdmin() && value.role !== user.role) {
      calls.push(this.userService.changeRole(user.id, value.role));
    }
    const newClientId = value.client?.id ?? null;
    if (value.role === 'CLIENT' && newClientId !== user.clientId) {
      calls.push(this.userService.linkClient(user.id, newClientId));
    }
    if (!calls.length) {
      this.isEditing.set(false);
      return;
    }

    this.formError.set(null);
    this.isSaving.set(true);
    this.userForm.disable();
    // Les appels se font l'un après l'autre : le rôle d'abord, puis le rattachement.
    concat(...calls).pipe(last()).subscribe({
      next: (saved) => {
        this.userForm.enable();
        this.isSaving.set(false);
        this.userForm.markAsPristine();
        this.notifications.success('users.updated', { name: `${saved.firstName} ${saved.lastName}` });
        this.selected.set(saved);
        this.loadLinkedClientName(saved);
        this.isEditing.set(false);
        this.load();
      },
      error: (error: unknown) => {
        this.userForm.enable();
        this.isSaving.set(false);
        this.formError.set(this.notifications.errorText(error));
      }
    });
  }
}

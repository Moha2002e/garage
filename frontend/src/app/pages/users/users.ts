import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormsModule,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TableLazyLoadEvent } from 'primeng/types/table';
import { AutoComplete, AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';
import { Select } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { Observable, Subject, concat, debounceTime, last } from 'rxjs';
import { HasUnsavedChanges } from '../../core/unsaved-changes.guard';
import { Role } from '../../models/auth.model';
import { ApiError } from '../../models/api-error.model';
import { Client } from '../../models/client.model';
import { UserAccount } from '../../models/user.model';
import { AuthService } from '../../services/auth.service';
import { ClientService } from '../../services/client.service';
import { LeaveConfirmService } from '../../services/leave-confirm.service';
import { NotificationService } from '../../services/notification.service';
import { UserService } from '../../services/user.service';

type ClientOption = Pick<Client, 'id' | 'firstName' | 'lastName'>;

const ROLES: Role[] = ['ADMIN', 'EMPLOYEE', 'CLIENT'];

type DialogMode = 'view' | 'edit' | 'create' | 'confirm';

// Règles du back-end : 12 à 72 caractères avec une majuscule, une minuscule et un chiffre.
const PASSWORD_MIN = 12;
const PASSWORD_MAX = 72;

function passwordRules(value: string): { length: boolean; upper: boolean; lower: boolean; digit: boolean } {
  return {
    length: value.length >= PASSWORD_MIN && value.length <= PASSWORD_MAX,
    upper: /[A-Z]/.test(value),
    lower: /[a-z]/.test(value),
    digit: /[0-9]/.test(value)
  };
}

function passwordValidator(control: AbstractControl<string>): ValidationErrors | null {
  const rules = passwordRules(control.value ?? '');
  return rules.length && rules.upper && rules.lower && rules.digit ? null : { passwordRules: true };
}

@Component({
  selector: 'app-users',
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
    Password,
    Select,
    Tag,
    ToggleSwitch
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
  private readonly translate = inject(TranslateService);
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

  // Fenêtre de dialogue
  protected readonly dialogOpen = signal(false);
  protected readonly mode = signal<DialogMode>('view');
  protected readonly selected = signal<UserAccount | null>(null);
  protected readonly linkedClientName = signal('');
  protected readonly isSaving = signal(false);
  protected readonly formError = signal<string | null>(null);
  // Erreurs renvoyées par le back-end, affichées sous le champ concerné.
  protected readonly emailTaken = signal(false);
  protected readonly passwordServerError = signal<string | null>(null);
  protected readonly selectedName = computed(() => {
    const u = this.selected();
    return u ? `${u.firstName} ${u.lastName}` : '';
  });
  protected readonly isSelf = computed(() => {
    const u = this.selected();
    return !!u && u.id === this.auth.user()?.id;
  });

  protected readonly createForm = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, passwordValidator]],
    role: this.fb.control<Role>('EMPLOYEE')
  });
  private readonly passwordValue = toSignal(this.createForm.controls.password.valueChanges, {
    initialValue: ''
  });
  protected readonly passwordChecks = computed(() => {
    const rules = passwordRules(this.passwordValue());
    return [
      { key: 'users.ruleLength', ok: rules.length },
      { key: 'users.ruleUpper', ok: rules.upper },
      { key: 'users.ruleLower', ok: rules.lower },
      { key: 'users.ruleDigit', ok: rules.digit }
    ];
  });

  protected readonly userForm = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    role: this.fb.control<Role>('CLIENT'),
    enabled: this.fb.control(true),
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

  // ---------- Fenêtre de dialogue ----------

  protected open(user: UserAccount): void {
    this.selected.set(user);
    this.mode.set('view');
    this.formError.set(null);
    this.dialogOpen.set(true);
    this.loadLinkedClientName(user);
  }

  protected startCreate(): void {
    this.createForm.reset({ firstName: '', lastName: '', email: '', password: '', role: 'EMPLOYEE' });
    this.formError.set(null);
    this.emailTaken.set(false);
    this.passwordServerError.set(null);
    this.selected.set(null);
    this.mode.set('create');
    this.dialogOpen.set(true);
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
      firstName: user.firstName,
      lastName: user.lastName,
      role: user.role,
      enabled: user.enabled,
      client: user.clientId
        ? { id: user.clientId, firstName: names[0] ?? '', lastName: names.slice(1).join(' ') }
        : null
    });
    // Seul l'ADMIN change le nom, le rôle et l'activation ; un admin ne touche pas à son propre rôle ni à son activation.
    if (!this.auth.isAdmin()) {
      this.userForm.controls.firstName.disable();
      this.userForm.controls.lastName.disable();
      this.userForm.controls.role.disable();
      this.userForm.controls.enabled.disable();
    } else if (this.isSelf()) {
      this.userForm.controls.role.disable();
      this.userForm.controls.enabled.disable();
    }
    this.formError.set(null);
    this.mode.set('edit');
  }

  protected startConfirm(): void {
    this.mode.set('confirm');
  }

  protected async cancelEdit(): Promise<void> {
    if (this.hasUnsavedChanges() && !(await this.leaveConfirm.confirmLeave())) {
      return;
    }
    this.mode.set(this.selected() ? 'view' : 'create');
    if (!this.selected()) {
      this.dialogOpen.set(false);
    }
  }

  protected async requestClose(): Promise<void> {
    if (this.hasUnsavedChanges() && !(await this.leaveConfirm.confirmLeave())) {
      return;
    }
    this.dialogOpen.set(false);
  }

  /** Fermeture par le fond ou Échap (seulement possible sans modification en cours). */
  protected onDialogHidden(): void {
    if (!this.dialogOpen()) {
      this.mode.set('view');
      this.selected.set(null);
    }
  }

  hasUnsavedChanges(): boolean {
    if (!this.dialogOpen()) {
      return false;
    }
    switch (this.mode()) {
      case 'edit':
        return this.userForm.dirty;
      case 'create':
        return this.createForm.dirty;
      default:
        return false;
    }
  }

  protected searchClients(event: AutoCompleteCompleteEvent): void {
    this.clientService.search(event.query, 0, 25, true).subscribe({
      next: (page) => this.clientSuggestions.set(page.content),
      error: (error: unknown) => this.notifications.error(error)
    });
  }

  // ---------- Enregistrement ----------

  protected create(): void {
    this.formError.set(null);
    this.emailTaken.set(false);
    this.passwordServerError.set(null);
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      this.formError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    const value = this.createForm.getRawValue();
    this.isSaving.set(true);
    this.createForm.disable();
    this.userService
      .create({
        firstName: value.firstName.trim(),
        lastName: value.lastName.trim(),
        email: value.email.trim(),
        password: value.password,
        role: value.role
      })
      .subscribe({
        next: (saved) => {
          this.createForm.enable();
          this.isSaving.set(false);
          this.createForm.markAsPristine();
          this.notifications.success('users.created', { name: `${saved.firstName} ${saved.lastName}` });
          this.dialogOpen.set(false);
          this.reloadFromStart();
        },
        error: (error: unknown) => {
          this.createForm.enable();
          this.isSaving.set(false);
          this.handleCreateError(error);
        }
      });
  }

  /** 409 : e-mail déjà utilisé. 400 : règles du mot de passe (ou autre champ refusé). */
  private handleCreateError(error: unknown): void {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 409) {
        this.emailTaken.set(true);
        return;
      }
      if (error.status === 400) {
        const fields = (error.error as Partial<ApiError> | null)?.fieldErrors ?? {};
        if (fields['password']) {
          this.passwordServerError.set(fields['password']);
        } else if (fields['email']) {
          this.formError.set(fields['email']);
        } else {
          this.passwordServerError.set(this.translate.instant('users.passwordRejected'));
          const message = this.notifications.errorText(error);
          if (message !== this.translate.instant('errors.generic')) {
            this.formError.set(message);
          }
        }
        return;
      }
    }
    this.formError.set(this.notifications.errorText(error));
  }

  protected save(): void {
    const user = this.selected();
    if (!user) {
      return;
    }
    if (this.userForm.invalid) {
      this.userForm.markAllAsTouched();
      this.formError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    const value = this.userForm.getRawValue();
    const calls: Observable<UserAccount>[] = [];
    const accountChanged =
      value.firstName.trim() !== user.firstName ||
      value.lastName.trim() !== user.lastName ||
      value.role !== user.role ||
      value.enabled !== user.enabled;
    if (this.auth.isAdmin() && accountChanged) {
      calls.push(
        this.userService.update(user.id, {
          firstName: value.firstName.trim(),
          lastName: value.lastName.trim(),
          role: value.role,
          enabled: value.enabled
        })
      );
    }
    const newClientId = value.client?.id ?? null;
    if (value.role === 'CLIENT' && newClientId !== user.clientId) {
      calls.push(this.userService.linkClient(user.id, newClientId));
    }
    if (!calls.length) {
      this.mode.set('view');
      return;
    }
    this.runUpdate(calls, 'users.updated');
  }

  /** Désactivation ou réactivation du compte, après confirmation dans la fenêtre. */
  protected confirmToggleEnabled(): void {
    const user = this.selected();
    if (!user) {
      return;
    }
    const enabling = !user.enabled;
    this.runUpdate(
      [
        this.userService.update(user.id, {
          firstName: user.firstName,
          lastName: user.lastName,
          role: user.role,
          enabled: enabling
        })
      ],
      enabling ? 'users.enabledDone' : 'users.disabledDone'
    );
  }

  private runUpdate(calls: Observable<UserAccount>[], successKey: string): void {
    this.formError.set(null);
    this.isSaving.set(true);
    this.userForm.disable();
    // Les appels se font l'un après l'autre : le compte d'abord, puis le rattachement.
    concat(...calls).pipe(last()).subscribe({
      next: (saved) => {
        this.userForm.enable();
        this.isSaving.set(false);
        this.userForm.markAsPristine();
        this.notifications.success(successKey, { name: `${saved.firstName} ${saved.lastName}` });
        this.selected.set(saved);
        this.loadLinkedClientName(saved);
        this.mode.set('view');
        this.load();
      },
      error: (error: unknown) => {
        this.userForm.enable();
        this.isSaving.set(false);
        if (this.mode() === 'confirm') {
          // Ex. un admin ne peut pas se désactiver : le back-end explique le refus.
          this.mode.set('view');
          this.notifications.error(error);
        } else {
          this.formError.set(this.notifications.errorText(error));
        }
      }
    });
  }
}

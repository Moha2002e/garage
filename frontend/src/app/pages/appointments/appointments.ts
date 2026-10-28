import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  FormsModule,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AutoComplete, AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { Button } from 'primeng/button';
import { DatePicker } from 'primeng/datepicker';
import { Dialog } from 'primeng/dialog';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { Textarea } from 'primeng/textarea';
import { formatDuration, formatMoney } from '../../core/format';
import { appointmentSeverity } from '../../core/status';
import { HasUnsavedChanges } from '../../core/unsaved-changes.guard';
import { Appointment, AppointmentStatus, CalendarEvent } from '../../models/appointment.model';
import { Client } from '../../models/client.model';
import { Quote, QuoteStatus } from '../../models/quote.model';
import { AppointmentService } from '../../services/appointment.service';
import { AuthService } from '../../services/auth.service';
import { ClientService } from '../../services/client.service';
import { LeaveConfirmService } from '../../services/leave-confirm.service';
import { NotificationService } from '../../services/notification.service';
import { QuoteService } from '../../services/quote.service';
import { SettingsService } from '../../services/settings.service';
import { AppointmentCalendar, CalendarMove, CalendarRange } from './appointment-calendar';

type DialogMode = 'view' | 'edit' | 'create' | 'confirm' | 'quoteEdit' | 'quoteConfirm';
type StatusAction = 'IN_PROGRESS' | 'CANCELLED' | 'COMPLETED';
type QuoteAction = 'ACCEPTED' | 'REFUSED';
type ClientOption = Pick<Client, 'id' | 'firstName' | 'lastName'>;
type QuoteLineGroup = FormGroup<{
  description: FormControl<string>;
  quantity: FormControl<number>;
  unitPrice: FormControl<number>;
}>;

const STATUSES: AppointmentStatus[] = ['PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];

// Durées proposées : de 15 minutes à 8 heures, par pas de 15 minutes.
const DURATIONS = Array.from({ length: 32 }, (_, i) => (i + 1) * 15).map((minutes) => ({
  minutes,
  label: formatDuration(minutes)
}));
const MAX_QUOTE_LINES = 50;

/** Un devis doit avoir au moins une ligne. */
function minLines(control: AbstractControl): ValidationErrors | null {
  return (control as FormArray).length >= 1 ? null : { minLines: true };
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
    Dialog,
    AppointmentCalendar,
    InputNumber,
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
  private readonly quoteService = inject(QuoteService);
  private readonly notifications = inject(NotificationService);
  private readonly leaveConfirm = inject(LeaveConfirmService);
  private readonly translate = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);
  protected readonly auth = inject(AuthService);
  protected readonly settings = inject(SettingsService);

  protected readonly statuses = STATUSES;
  protected readonly durations = DURATIONS;
  protected readonly maxQuoteLines = MAX_QUOTE_LINES;

  // Un compte CLIENT sans fiche client ne voit rien : on affiche un message à la place de la table.
  protected readonly isUnlinkedClient = computed(() => {
    const user = this.auth.user();
    return user?.role === 'CLIENT' && !user.clientLinked;
  });

  // Calendrier
  private readonly calendar = viewChild(AppointmentCalendar);
  private readonly allEvents = signal<CalendarEvent[]>([]);
  private readonly range = signal<CalendarRange | null>(null);
  protected readonly isLoading = signal(false);
  // Rendez-vous dont le déplacement est en cours d'enregistrement.
  protected readonly busyIds = signal<string[]>([]);
  protected readonly statusFilter = signal<AppointmentStatus | null>(null);
  protected readonly events = computed(() => {
    const status = this.statusFilter();
    return status ? this.allEvents().filter((e) => e.status === status) : this.allEvents();
  });

  // Fenêtre de dialogue
  protected readonly dialogOpen = signal(false);
  protected readonly mode = signal<DialogMode>('view');
  protected readonly selected = signal<Appointment | null>(null);
  protected readonly statusAction = signal<StatusAction>('CANCELLED');
  protected readonly isSaving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly selectedName = computed(() => {
    const a = this.selected();
    return a ? `${a.clientFirstName} ${a.clientLastName}` : '';
  });
  // Modifier : seulement un rendez-vous planifié.
  protected readonly canChange = computed(
    () => this.auth.canWrite() && this.selected()?.status === 'PLANNED'
  );
  protected readonly canStart = this.canChange;
  // Terminer et annuler : planifié ou en cours.
  protected readonly canFinish = computed(() => {
    const status = this.selected()?.status;
    return this.auth.canWrite() && (status === 'PLANNED' || status === 'IN_PROGRESS');
  });

  // Champ « Déplacer » du détail (alternative clavier au glisser déposer) : null tant qu'on n'a rien changé.
  protected readonly moveDraft = signal<Date | null>(null);
  protected readonly moveValue = computed(() => {
    const a = this.selected();
    return this.moveDraft() ?? (a ? new Date(a.date) : null);
  });
  protected readonly moveChanged = computed(() => {
    const a = this.selected();
    const draft = this.moveDraft();
    return !!a && !!draft && draft.getTime() !== new Date(a.date).getTime();
  });
  protected readonly isMoving = computed(() => {
    const a = this.selected();
    return !!a && this.busyIds().includes(a.id);
  });

  // Devis du rendez-vous ouvert (null = pas de devis)
  protected readonly quote = signal<Quote | null>(null);
  protected readonly quoteLoading = signal(false);
  protected readonly quoteAction = signal<QuoteAction>('ACCEPTED');
  protected readonly canEditQuote = computed(() => {
    const q = this.quote();
    return (
      this.auth.canWrite() &&
      this.selected()?.status !== 'CANCELLED' &&
      (q === null || q.status === 'DRAFT')
    );
  });
  protected readonly canDecideQuote = computed(
    () => this.auth.canWrite() && this.quote()?.status === 'DRAFT'
  );

  // Formulaire
  protected readonly clientSuggestions = signal<ClientOption[]>([]);
  protected readonly vehicleOptions = signal<{ id: string; label: string }[]>([]);
  protected readonly appointmentForm = this.fb.group({
    client: this.fb.control<ClientOption | null>(null, Validators.required),
    vehicleId: ['', Validators.required],
    date: this.fb.control<Date | null>(null, Validators.required),
    durationMinutes: this.fb.control(60, [Validators.required, Validators.min(15), Validators.max(480)]),
    reason: ['', [Validators.required, Validators.maxLength(500)]]
  });

  protected readonly quoteForm = this.fb.group({
    lines: this.fb.array<QuoteLineGroup>([], [minLines, Validators.maxLength(MAX_QUOTE_LINES)])
  });

  protected readonly clientLabel = (client: ClientOption): string =>
    `${client.firstName} ${client.lastName}`;

  ngOnInit(): void {
    // Lien depuis l'accueil : ?open=<id> ouvre directement le détail.
    const id = this.route.snapshot.queryParamMap.get('open');
    if (id) {
      this.openById(id, true);
    }
  }

  // ---------- Calendrier ----------

  protected onRangeChange(range: CalendarRange): void {
    this.range.set(range);
    this.load();
  }

  protected onStatusChange(status: AppointmentStatus | null): void {
    this.statusFilter.set(status);
  }

  /** silent : rechargement léger (sans voile de chargement ni toast d'erreur), pour l'avancement en direct. */
  protected load(silent = false): void {
    const range = this.range();
    if (this.isUnlinkedClient() || !range) {
      return;
    }
    if (silent && (this.isLoading() || this.busyIds().length > 0)) {
      return;
    }
    if (!silent) {
      this.isLoading.set(true);
    }
    this.appointmentService.calendar(range.from, range.to).subscribe({
      next: (list) => {
        // Une réponse tardive d'une ancienne plage ne remplace pas la plage actuelle.
        if (this.range() === range && (!silent || this.busyIds().length === 0)) {
          this.allEvents.set(list);
          this.isLoading.set(false);
        }
      },
      error: (error: unknown) => {
        if (!silent) {
          this.isLoading.set(false);
          this.notifications.error(error);
        }
      }
    });
  }

  // ---------- Déplacement (glisser déposer ou champ « Déplacer ») ----------

  protected onEventMove(move: CalendarMove): void {
    this.reschedule(move.event.id, move.start);
  }

  protected moveFromDialog(): void {
    const a = this.selected();
    const date = this.moveDraft();
    if (a && date) {
      this.reschedule(a.id, date);
    }
  }

  private patchEvent(id: string, patch: Partial<CalendarEvent>): void {
    this.allEvents.update((list) => list.map((e) => (e.id === id ? { ...e, ...patch } : e)));
  }

  /**
   * Mise à jour optimiste : l'événement bouge tout de suite, puis le back-end valide.
   * Refus (400 ou 409 : conflit de véhicule, capacité, statut) : retour à la place d'origine et message du back-end.
   */
  private reschedule(id: string, wanted: Date): void {
    if (this.busyIds().includes(id)) {
      return;
    }
    const start = new Date(wanted);
    start.setSeconds(0, 0);
    const previous = this.allEvents().find((e) => e.id === id);
    if (previous) {
      const minutes =
        previous.durationMinutes ||
        Math.max(15, Math.round((new Date(previous.end).getTime() - new Date(previous.start).getTime()) / 60000));
      this.patchEvent(id, {
        start: start.toISOString(),
        end: new Date(start.getTime() + minutes * 60000).toISOString()
      });
    }
    this.busyIds.update((list) => [...list, id]);
    this.appointmentService.rescheduleAppointment(id, start.toISOString()).subscribe({
      next: (saved) => {
        this.busyIds.update((list) => list.filter((x) => x !== id));
        this.patchEvent(id, {
          start: saved.date,
          end: new Date(new Date(saved.date).getTime() + saved.durationMinutes * 60000).toISOString(),
          durationMinutes: saved.durationMinutes,
          status: saved.status
        });
        if (!previous) {
          this.load(true);
        }
        this.calendar()?.flash(id);
        this.notifications.success('calendar.moved', {
          name: `${saved.clientFirstName} ${saved.clientLastName}`,
          date: new Date(saved.date).toLocaleString(this.settings.language() === 'en' ? 'en-GB' : 'fr-BE', {
            dateStyle: 'medium',
            timeStyle: 'short'
          })
        });
        if (this.selected()?.id === id) {
          this.selected.set(saved);
          this.moveDraft.set(null);
        }
      },
      error: (error: unknown) => {
        this.busyIds.update((list) => list.filter((x) => x !== id));
        if (previous) {
          this.patchEvent(id, { start: previous.start, end: previous.end });
        }
        this.notifications.error(error);
      }
    });
  }

  protected onEventClick(event: CalendarEvent): void {
    this.openById(event.id, false);
  }

  /** Le calendrier ne porte qu'un résumé : on charge le rendez-vous complet avant d'ouvrir le détail. */
  private openById(id: string, moveCalendar: boolean): void {
    this.appointmentService.getById(id).subscribe({
      next: (appointment) => {
        if (moveCalendar) {
          this.calendar()?.goTo(new Date(appointment.date));
        }
        this.open(appointment);
      },
      error: (error: unknown) => this.notifications.error(error)
    });
  }

  // ---------- Fenêtre de dialogue ----------

  private open(appointment: Appointment): void {
    this.selected.set(appointment);
    this.mode.set('view');
    this.formError.set(null);
    this.quote.set(null);
    this.moveDraft.set(null);
    this.dialogOpen.set(true);
    this.loadQuote(appointment.id);
  }

  /** Heure de fin = début plus durée. */
  protected endDate(appointment: Appointment): Date {
    return new Date(new Date(appointment.date).getTime() + appointment.durationMinutes * 60000);
  }

  protected duration(minutes: number): string {
    return formatDuration(minutes);
  }

  protected money(value: number | null | undefined): string {
    return formatMoney(value, this.settings.language());
  }

  /** Nouveau rendez-vous ; un clic sur un jour du calendrier pré-remplit la date. */
  protected startCreate(date?: Date): void {
    this.selected.set(null);
    this.quote.set(null);
    this.appointmentForm.reset({ date: date ?? null });
    this.vehicleOptions.set([]);
    this.formError.set(null);
    this.mode.set('create');
    this.dialogOpen.set(true);
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
      durationMinutes: a.durationMinutes,
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
      this.closeDialog();
    } else {
      this.mode.set('view');
    }
  }

  protected async requestClose(): Promise<void> {
    if (this.hasUnsavedChanges() && !(await this.leaveConfirm.confirmLeave())) {
      return;
    }
    this.closeDialog();
  }

  hasUnsavedChanges(): boolean {
    if (!this.dialogOpen()) {
      return false;
    }
    switch (this.mode()) {
      case 'edit':
      case 'create':
        return this.appointmentForm.dirty;
      case 'quoteEdit':
        return this.quoteForm.dirty;
      default:
        return false;
    }
  }

  private closeDialog(): void {
    this.dialogOpen.set(false);
    this.mode.set('view');
    this.selected.set(null);
    this.moveDraft.set(null);
  }

  /** Fermeture par le fond ou Échap (seulement possible sans modification en cours). */
  protected onDialogHidden(): void {
    if (!this.dialogOpen()) {
      this.mode.set('view');
      this.selected.set(null);
    }
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
    this.clientService.getVehicles(clientId, true).subscribe({
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
      durationMinutes: value.durationMinutes,
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
          this.closeDialog();
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

  /** Annulation ou fin du rendez-vous, après confirmation dans la fenêtre. */
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
        const messageKey = {
          IN_PROGRESS: 'appointments.started',
          COMPLETED: 'appointments.completed',
          CANCELLED: 'appointments.cancelled'
        }[action];
        this.notifications.success(messageKey, {
          name: `${saved.clientFirstName} ${saved.clientLastName}`
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

  /** Clé de traduction de la question de confirmation selon l'action choisie. */
  protected confirmKey(): string {
    switch (this.statusAction()) {
      case 'IN_PROGRESS':
        return 'appointments.confirmStart';
      case 'COMPLETED':
        return 'appointments.confirmComplete';
      default:
        return 'appointments.confirmCancel';
    }
  }

  protected quoteSeverity(status: QuoteStatus): 'info' | 'success' | 'danger' {
    switch (status) {
      case 'ACCEPTED':
        return 'success';
      case 'REFUSED':
        return 'danger';
      default:
        return 'info';
    }
  }

  protected statusSeverity = appointmentSeverity;

  // ---------- Devis ----------

  protected get lines(): FormArray<QuoteLineGroup> {
    return this.quoteForm.controls.lines;
  }

  private loadQuote(appointmentId: string): void {
    this.quoteLoading.set(true);
    this.quoteService.get(appointmentId).subscribe({
      next: (quote) => {
        // La fenêtre a pu changer de rendez-vous pendant l'attente.
        if (this.selected()?.id === appointmentId) {
          this.quote.set(quote ?? null);
        }
        this.quoteLoading.set(false);
      },
      error: (error: unknown) => {
        this.quoteLoading.set(false);
        this.notifications.error(error);
      }
    });
  }

  private newLine(description = '', quantity = 1, unitPrice = 0): QuoteLineGroup {
    return this.fb.group({
      description: [description, [Validators.required, Validators.maxLength(255)]],
      quantity: this.fb.control(quantity, [Validators.required, Validators.min(0.01)]),
      unitPrice: this.fb.control(unitPrice, [Validators.required, Validators.min(0)])
    });
  }

  protected startQuoteEdit(): void {
    const quote = this.quote();
    this.lines.clear();
    if (quote) {
      quote.lines.forEach((l) => this.lines.push(this.newLine(l.description, l.quantity, l.unitPrice)));
    } else {
      this.lines.push(this.newLine());
    }
    this.quoteForm.markAsPristine();
    this.formError.set(null);
    this.mode.set('quoteEdit');
  }

  protected addLine(): void {
    if (this.lines.length < MAX_QUOTE_LINES) {
      this.lines.push(this.newLine());
      this.quoteForm.markAsDirty();
    }
  }

  protected removeLine(index: number): void {
    this.lines.removeAt(index);
    this.quoteForm.markAsDirty();
  }

  protected lineTotal(line: QuoteLineGroup): number {
    const { quantity, unitPrice } = line.getRawValue();
    return Math.round((quantity ?? 0) * (unitPrice ?? 0) * 100) / 100;
  }

  protected draftTotal(): number {
    return this.lines.controls.reduce((sum, line) => sum + this.lineTotal(line), 0);
  }

  protected saveQuote(): void {
    this.formError.set(null);
    const appointment = this.selected();
    if (this.quoteForm.invalid || !appointment) {
      this.quoteForm.markAllAsTouched();
      this.formError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    const request = {
      lines: this.lines.getRawValue().map((l) => ({
        description: l.description.trim(),
        quantity: l.quantity,
        unitPrice: l.unitPrice
      }))
    };
    const wasCreate = this.quote() === null;
    this.isSaving.set(true);
    this.quoteForm.disable();
    this.quoteService.save(appointment.id, request).subscribe({
      next: (saved) => {
        this.quoteForm.enable();
        this.isSaving.set(false);
        this.quoteForm.markAsPristine();
        this.notifications.success(wasCreate ? 'quote.created' : 'quote.updated', {
          name: `${appointment.clientFirstName} ${appointment.clientLastName}`
        });
        this.applyQuote(saved);
        this.mode.set('view');
      },
      error: (error: unknown) => {
        this.quoteForm.enable();
        this.isSaving.set(false);
        this.formError.set(this.notifications.errorText(error));
      }
    });
  }

  protected startQuoteConfirm(action: QuoteAction): void {
    this.quoteAction.set(action);
    this.mode.set('quoteConfirm');
  }

  /** Accepter ou refuser le devis, après confirmation dans la fenêtre. */
  protected confirmQuoteStatus(): void {
    const appointment = this.selected();
    if (!appointment) {
      return;
    }
    const action = this.quoteAction();
    this.isSaving.set(true);
    this.quoteService.changeStatus(appointment.id, action).subscribe({
      next: (saved) => {
        this.isSaving.set(false);
        this.notifications.success(action === 'ACCEPTED' ? 'quote.accepted' : 'quote.refused', {
          name: `${appointment.clientFirstName} ${appointment.clientLastName}`
        });
        this.applyQuote(saved);
        this.mode.set('view');
      },
      error: (error: unknown) => {
        this.isSaving.set(false);
        this.mode.set('view');
        this.notifications.error(error);
      }
    });
  }

  /** Garde le devis affiché, le résumé du rendez-vous ouvert et la liste synchronisés. */
  private applyQuote(quote: Quote): void {
    this.quote.set(quote);
    const current = this.selected();
    if (current) {
      this.selected.set({ ...current, quoteStatus: quote.status, quoteTotal: quote.total });
    }
    this.load();
  }
}

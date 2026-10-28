import { DatePipe } from '@angular/common';
import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { Button } from 'primeng/button';
import { SelectButton } from 'primeng/selectbutton';
import { Tag } from 'primeng/tag';
import { appointmentSeverity } from '../../core/status';
import { CalendarEvent } from '../../models/appointment.model';
import { SettingsService } from '../../services/settings.service';

export type CalendarView = 'day' | 'week' | 'month';

/** Plage affichée : from inclus, to exclu (premier instant du jour suivant la dernière case). */
export interface CalendarRange {
  from: Date;
  to: Date;
}

/** Demande de déplacement issue du glisser déposer ; la page appelle le back-end. */
export interface CalendarMove {
  event: CalendarEvent;
  start: Date;
}

interface DayCell {
  date: Date;
  ts: number;
  key: string;
  inMonth: boolean;
  isToday: boolean;
  events: CalendarEvent[];
}

interface PositionedEvent {
  event: CalendarEvent;
  top: number;
  height: number;
  left: number;
  width: number;
  compact: boolean;
  from: string;
  to: string;
}

interface GridColumn {
  date: Date;
  ts: number;
  isToday: boolean;
  weekday: string;
  label: string;
  events: PositionedEvent[];
}

interface DragState {
  event: CalendarEvent;
  mode: 'grid' | 'month';
  startX: number;
  startY: number;
  x: number;
  y: number;
  // Distance entre le pointeur et le haut du bloc saisi.
  grabY: number;
  durationMin: number;
  active: boolean;
  // Jour cible (minuit local, en ms) et minute de début dans ce jour ; null hors d'une cible.
  dayTs: number | null;
  minutes: number | null;
}

// Toute la journée est affichée (0 h à 24 h) : aucun rendez-vous ne peut sortir de la grille.
const HOURS = Array.from({ length: 24 }, (_, i) => i);
const HOUR_LABELS = HOURS.map((h) => `${String(h).padStart(2, '0')}:00`);
const HOUR_PX_WEEK = 48;
const HOUR_PX_DAY = 80;
const MIN_EVENT_PX = 24;
const COMPACT_PX = 56;
const SCROLL_HOUR = 7;
const SNAP_MINUTES = 15;
const DRAG_THRESHOLD_PX = 5;
const EDGE_TOP_PX = 96;
const EDGE_BOTTOM_PX = 48;
const MAX_CHIPS = 3;
// Heure proposée quand on clique sur un jour en vue mois.
const DEFAULT_HOUR = 9;
const TICK_MS = 60_000;

function dayKey(d: Date): string {
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}

function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

function addDays(d: Date, n: number): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
}

/** Lundi de la semaine de la date donnée, à minuit. */
function mondayOf(d: Date): Date {
  const offset = (d.getDay() + 6) % 7;
  return addDays(d, -offset);
}

function hhmm(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
}

function durationOf(e: CalendarEvent): number {
  if (e.durationMinutes > 0) {
    return e.durationMinutes;
  }
  return Math.max(SNAP_MINUTES, Math.round((new Date(e.end).getTime() - new Date(e.start).getTime()) / 60000));
}

/**
 * Calendrier léger maison (vues jour, semaine et mois) fait avec des composants PrimeNG.
 * Il ne charge rien lui même : il annonce la plage visible (rangeChange) et reçoit les événements.
 * Le glisser déposer utilise les Pointer Events (souris, tactile et stylet avec le même code) ;
 * il ne modifie rien : il annonce le déplacement voulu (eventMove) et la page appelle le back-end.
 */
@Component({
  selector: 'app-appointment-calendar',
  imports: [DatePipe, FormsModule, TranslatePipe, Button, SelectButton, Tag],
  templateUrl: './appointment-calendar.html',
  styleUrls: ['./appointment-calendar.css', './appointment-calendar-day.css']
})
export class AppointmentCalendar {
  private readonly settings = inject(SettingsService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly events = input<CalendarEvent[]>([]);
  readonly loading = input(false);
  /** Droit de déplacer les rendez-vous planifiés. */
  readonly editable = input(false);
  /** Rendez-vous dont un déplacement est en cours d'enregistrement : non déplaçables. */
  readonly busyIds = input<readonly string[]>([]);

  readonly rangeChange = output<CalendarRange>();
  readonly eventClick = output<CalendarEvent>();
  readonly dayClick = output<Date>();
  readonly eventMove = output<CalendarMove>();
  /** Demande un rechargement léger (toutes les minutes tant qu'un rendez-vous est en cours). */
  readonly refresh = output<void>();

  protected readonly view = signal<CalendarView>('week');
  private readonly cursor = signal(new Date());
  private readonly now = signal(new Date());
  protected readonly flashId = signal<string | null>(null);
  protected readonly drag = signal<DragState | null>(null);
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  protected readonly maxChips = MAX_CHIPS;
  protected readonly hours = HOURS;
  protected readonly hourLabels = HOUR_LABELS;
  protected readonly hourPx = computed(() => (this.view() === 'day' ? HOUR_PX_DAY : HOUR_PX_WEEK));
  protected readonly viewOptions = [
    { value: 'day' as CalendarView, label: 'calendar.day' },
    { value: 'week' as CalendarView, label: 'calendar.week' },
    { value: 'month' as CalendarView, label: 'calendar.month' }
  ];
  protected readonly severity = appointmentSeverity;

  private readonly locale = computed(() => (this.settings.language() === 'en' ? 'en-GB' : 'fr-BE'));

  /** Première case, nombre de jours affichés. */
  private readonly grid = computed(() => {
    const c = this.cursor();
    switch (this.view()) {
      case 'day':
        return { start: startOfDay(c), days: 1 };
      case 'week':
        return { start: mondayOf(c), days: 7 };
      default: {
        const first = new Date(c.getFullYear(), c.getMonth(), 1);
        const daysInMonth = new Date(c.getFullYear(), c.getMonth() + 1, 0).getDate();
        const offset = (first.getDay() + 6) % 7;
        return { start: addDays(first, -offset), days: Math.ceil((offset + daysInMonth) / 7) * 7 };
      }
    }
  });

  readonly range = computed<CalendarRange>(() => {
    const { start, days } = this.grid();
    return { from: start, to: addDays(start, days) };
  });

  protected readonly title = computed(() => {
    const c = this.cursor();
    const loc = this.locale();
    const cap = (text: string) => text.charAt(0).toUpperCase() + text.slice(1);
    const fmt = (d: Date) => d.toLocaleDateString(loc, { day: 'numeric', month: 'short', year: 'numeric' });
    const { start } = this.grid();
    return {
      month: cap(c.toLocaleDateString(loc, { month: 'long', year: 'numeric' })),
      day: cap(c.toLocaleDateString(loc, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })),
      from: fmt(start),
      to: fmt(addDays(start, 6))
    };
  });

  protected readonly weekdays = computed(() => {
    const loc = this.locale();
    const monday = mondayOf(new Date(2024, 0, 3));
    return Array.from({ length: 7 }, (_, i) => addDays(monday, i).toLocaleDateString(loc, { weekday: 'short' }));
  });

  private readonly eventsByDay = computed(() => {
    const map = new Map<string, CalendarEvent[]>();
    const sorted = [...this.events()].sort((a, b) => new Date(a.start).getTime() - new Date(b.start).getTime());
    for (const e of sorted) {
      const key = dayKey(new Date(e.start));
      map.set(key, [...(map.get(key) ?? []), e]);
    }
    return map;
  });

  protected readonly monthCells = computed<DayCell[]>(() => {
    if (this.view() !== 'month') {
      return [];
    }
    const { start, days } = this.grid();
    const month = this.cursor().getMonth();
    const today = dayKey(this.now());
    const byDay = this.eventsByDay();
    return Array.from({ length: days }, (_, i) => {
      const date = addDays(start, i);
      const key = dayKey(date);
      return {
        date,
        ts: date.getTime(),
        key,
        inMonth: date.getMonth() === month,
        isToday: key === today,
        events: byDay.get(key) ?? []
      };
    });
  });

  /** Colonnes de la vue jour (une) ou semaine (sept). */
  protected readonly columns = computed<GridColumn[]>(() => {
    if (this.view() === 'month') {
      return [];
    }
    const { start, days } = this.grid();
    const loc = this.locale();
    const today = dayKey(this.now());
    const byDay = this.eventsByDay();
    const compactAllowed = this.view() === 'day';
    return Array.from({ length: days }, (_, i) => {
      const date = addDays(start, i);
      const key = dayKey(date);
      return {
        date,
        ts: date.getTime(),
        isToday: key === today,
        weekday: date.toLocaleDateString(loc, { weekday: 'short' }),
        label: date.toLocaleDateString(loc, { weekday: 'long', day: 'numeric', month: 'long' }),
        events: this.position(byDay.get(key) ?? [], this.hourPx(), compactAllowed)
      };
    });
  });

  /** Rendez-vous en cours du jour affiché (bandeau de la vue jour). */
  protected readonly liveEvents = computed(() =>
    this.view() === 'day'
      ? (this.columns()[0]?.events ?? []).map((p) => p.event).filter((e) => e.status === 'IN_PROGRESS')
      : []
  );
  protected readonly liveVehicles = computed(() => this.liveEvents().map((e) => e.vehicleLabel).join(', '));

  /** Position de la ligne « maintenant » dans la grille (px). */
  protected readonly nowTop = computed(() => {
    const n = this.now();
    return ((n.getHours() * 60 + n.getMinutes()) / 60) * this.hourPx();
  });

  protected readonly draggedId = computed(() => (this.drag()?.active ? this.drag()!.event.id : null));

  /** Aperçu du créneau cible pendant le glisser (vues jour et semaine). */
  protected readonly dropPreview = computed(() => {
    const d = this.drag();
    if (!d?.active || d.mode !== 'grid' || d.dayTs === null || d.minutes === null) {
      return null;
    }
    const end = Math.min(d.minutes + d.durationMin, 1440);
    return {
      dayTs: d.dayTs,
      top: (d.minutes / 60) * this.hourPx(),
      height: Math.max(((end - d.minutes) / 60) * this.hourPx(), MIN_EVENT_PX),
      from: hhmm(d.minutes),
      to: hhmm(end)
    };
  });

  /** Jour cible en vue mois. */
  protected readonly dropDay = computed(() => {
    const d = this.drag();
    return d?.active && d.mode === 'month' ? d.dayTs : null;
  });

  /** Étiquette qui suit le pointeur. */
  protected readonly ghost = computed(() => {
    const d = this.drag();
    if (!d?.active) {
      return null;
    }
    let when = '';
    if (d.dayTs !== null && d.minutes !== null) {
      const day = new Date(d.dayTs).toLocaleDateString(this.locale(), { weekday: 'short', day: 'numeric', month: 'short' });
      when = `${day} ${hhmm(d.minutes)}`;
    }
    return { x: d.x + 14, y: d.y + 14, name: d.event.clientName, when };
  });

  private scrollKey = '';
  private pendingFit = false;
  private sawLoading = false;
  private suppressClick = false;
  private raf = 0;
  private flashTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    // Le parent charge les événements à chaque changement de plage.
    effect(() => this.rangeChange.emit(this.range()));

    // Vues jour et semaine : défilement vers 7 h, ou vers le premier rendez-vous s'il est plus tôt.
    effect(() => {
      const view = this.view();
      const range = this.range();
      const loading = this.loading();
      const events = this.events();
      if (view === 'month') {
        this.scrollKey = '';
        return;
      }
      const key = `${view}|${range.from.getTime()}`;
      untracked(() => {
        if (key !== this.scrollKey) {
          this.scrollKey = key;
          this.pendingFit = true;
          this.sawLoading = loading;
          this.scrollToHour(SCROLL_HOUR);
          return;
        }
        if (loading) {
          this.sawLoading = true;
        }
        if (this.pendingFit && this.sawLoading && !loading) {
          this.pendingFit = false;
          const first = Math.min(
            ...events.map((e) => {
              const s = new Date(e.start);
              return s.getHours() * 60 + s.getMinutes();
            })
          );
          if (events.length && first < SCROLL_HOUR * 60) {
            this.scrollToHour(Math.max(0, Math.floor(first / 60) - 1));
          }
        }
      });
    });

    // Toutes les minutes : ligne « maintenant » et, si un rendez-vous est en cours, rechargement léger.
    const timer = setInterval(() => {
      this.now.set(new Date());
      if (
        this.view() !== 'month' &&
        !this.drag()?.active &&
        this.events().some((e) => e.status === 'IN_PROGRESS')
      ) {
        this.refresh.emit();
      }
    }, TICK_MS);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(timer);
      clearTimeout(this.flashTimer);
      this.stopDrag();
    });
  }

  // ---------- Navigation ----------

  protected previous(): void {
    this.shift(-1);
  }

  protected next(): void {
    this.shift(1);
  }

  protected today(): void {
    this.cursor.set(new Date());
  }

  /** Utilisé par la page pour afficher le jour d'un rendez-vous précis. */
  goTo(date: Date): void {
    this.cursor.set(date);
  }

  /** Petite confirmation visuelle sur un rendez-vous qui vient d'être déplacé. */
  flash(id: string): void {
    this.flashId.set(id);
    clearTimeout(this.flashTimer);
    this.flashTimer = setTimeout(() => this.flashId.set(null), 1800);
  }

  protected onViewChange(view: CalendarView | null): void {
    if (view) {
      this.view.set(view);
    }
  }

  protected showWeekOf(date: Date, event: Event): void {
    event.stopPropagation();
    this.cursor.set(date);
    this.view.set('week');
  }

  protected showDayOf(date: Date): void {
    this.cursor.set(date);
    this.view.set('day');
  }

  private shift(direction: 1 | -1): void {
    const c = this.cursor();
    switch (this.view()) {
      case 'day':
        this.cursor.set(addDays(c, direction));
        break;
      case 'week':
        this.cursor.set(addDays(c, 7 * direction));
        break;
      default:
        this.cursor.set(new Date(c.getFullYear(), c.getMonth() + direction, 1));
    }
  }

  private scrollToHour(hour: number): void {
    afterNextRender(
      () => {
        const el = this.scroller()?.nativeElement;
        if (el) {
          el.scrollTop = hour * this.hourPx();
        }
      },
      { injector: this.injector }
    );
  }

  // ---------- Clics et clavier ----------

  protected onDayClick(date: Date): void {
    this.dayClick.emit(new Date(date.getFullYear(), date.getMonth(), date.getDate(), DEFAULT_HOUR));
  }

  protected onSlotClick(date: Date, hour: number, event: Event): void {
    event.stopPropagation();
    this.dayClick.emit(new Date(date.getFullYear(), date.getMonth(), date.getDate(), hour));
  }

  /** Entrée ou espace créent un rendez-vous ; les flèches passent d'un créneau à l'autre. */
  protected onSlotKey(event: KeyboardEvent, col: number, hour: number, date: Date): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.onSlotClick(date, hour, event);
      return;
    }
    const dc = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    const dh = event.key === 'ArrowDown' ? 1 : event.key === 'ArrowUp' ? -1 : 0;
    if (dc || dh) {
      event.preventDefault();
      this.host.nativeElement.querySelector<HTMLElement>(`[data-slot="${col + dc}-${hour + dh}"]`)?.focus();
    }
  }

  protected onEventClick(event: CalendarEvent, domEvent: Event): void {
    domEvent.stopPropagation();
    // Un glisser qui se termine sur le bloc ne doit pas ouvrir le détail.
    if (this.suppressClick) {
      return;
    }
    this.eventClick.emit(event);
  }

  protected eventTooltip(e: CalendarEvent): string {
    return [e.clientName, e.vehicleLabel, e.title].filter(Boolean).join(', ');
  }

  protected progress(e: CalendarEvent): number {
    return Math.min(100, Math.max(0, Math.round(e.progressPercent ?? 0)));
  }

  // ---------- Glisser déposer (Pointer Events) ----------

  /** Seuls les rendez-vous planifiés sont déplaçables, et seulement avec le droit d'écriture. */
  protected canDrag(e: CalendarEvent): boolean {
    return this.editable() && e.status === 'PLANNED' && !this.busyIds().includes(e.id);
  }

  protected onPointerDown(ev: PointerEvent, event: CalendarEvent, mode: 'grid' | 'month'): void {
    if (!this.canDrag(event) || this.drag() || (ev.pointerType === 'mouse' && ev.button !== 0)) {
      return;
    }
    const rect = (ev.currentTarget as HTMLElement).getBoundingClientRect();
    this.drag.set({
      event,
      mode,
      startX: ev.clientX,
      startY: ev.clientY,
      x: ev.clientX,
      y: ev.clientY,
      grabY: ev.clientY - rect.top,
      durationMin: durationOf(event),
      active: false,
      dayTs: null,
      minutes: null
    });
    window.addEventListener('pointermove', this.onMove);
    window.addEventListener('pointerup', this.onUp);
    window.addEventListener('pointercancel', this.onAbort);
    window.addEventListener('keydown', this.onKey);
  }

  private readonly onMove = (ev: PointerEvent): void => {
    const d = this.drag();
    if (!d) {
      return;
    }
    if (!d.active) {
      if (Math.hypot(ev.clientX - d.startX, ev.clientY - d.startY) < DRAG_THRESHOLD_PX) {
        return;
      }
      window.getSelection()?.removeAllRanges();
      this.raf = requestAnimationFrame(this.autoScroll);
    }
    this.drag.set(this.withTarget({ ...d, active: true, x: ev.clientX, y: ev.clientY }));
  };

  private readonly onUp = (ev: PointerEvent): void => {
    const d = this.drag();
    this.stopDrag();
    if (!d?.active) {
      return;
    }
    // Le clic qui suit le relâchement ne doit pas ouvrir le détail.
    this.suppressClick = true;
    setTimeout(() => (this.suppressClick = false));
    const target = this.withTarget({ ...d, x: ev.clientX, y: ev.clientY });
    if (target.dayTs === null || target.minutes === null) {
      return;
    }
    const day = new Date(target.dayTs);
    const start = new Date(day.getFullYear(), day.getMonth(), day.getDate(), 0, target.minutes);
    if (start.getTime() !== new Date(d.event.start).getTime()) {
      this.eventMove.emit({ event: d.event, start });
    }
  };

  private readonly onAbort = (): void => {
    if (this.drag()?.active) {
      this.suppressClick = true;
      setTimeout(() => (this.suppressClick = false));
    }
    this.stopDrag();
  };

  private readonly onKey = (ev: KeyboardEvent): void => {
    if (ev.key === 'Escape') {
      this.onAbort();
    }
  };

  private stopDrag(): void {
    window.removeEventListener('pointermove', this.onMove);
    window.removeEventListener('pointerup', this.onUp);
    window.removeEventListener('pointercancel', this.onAbort);
    window.removeEventListener('keydown', this.onKey);
    cancelAnimationFrame(this.raf);
    this.drag.set(null);
  }

  /** Défilement automatique de la grille quand on approche son bord haut ou bas. */
  private readonly autoScroll = (): void => {
    const d = this.drag();
    if (!d?.active) {
      return;
    }
    const el = this.scroller()?.nativeElement;
    if (el && d.mode === 'grid') {
      const r = el.getBoundingClientRect();
      let dy = 0;
      if (d.y < r.top + EDGE_TOP_PX) {
        dy = -Math.ceil((r.top + EDGE_TOP_PX - d.y) / 4);
      } else if (d.y > r.bottom - EDGE_BOTTOM_PX) {
        dy = Math.ceil((d.y - (r.bottom - EDGE_BOTTOM_PX)) / 4);
      }
      if (dy) {
        const before = el.scrollTop;
        el.scrollTop += Math.max(-20, Math.min(20, dy));
        if (el.scrollTop !== before) {
          this.drag.set(this.withTarget(d));
        }
      }
    }
    this.raf = requestAnimationFrame(this.autoScroll);
  };

  /** Calcule le jour et l'heure visés sous le pointeur (créneaux de 15 minutes, durée conservée). */
  private withTarget(d: DragState): DragState {
    const col = document.elementFromPoint(d.x, d.y)?.closest<HTMLElement>('[data-drop]');
    if (!col) {
      return { ...d, dayTs: null, minutes: null };
    }
    const dayTs = Number(col.dataset['drop']);
    if (d.mode === 'month') {
      // Vue mois : on change de jour, l'heure reste celle du rendez-vous.
      const s = new Date(d.event.start);
      return { ...d, dayTs, minutes: s.getHours() * 60 + s.getMinutes() };
    }
    const raw = ((d.y - d.grabY - col.getBoundingClientRect().top) / this.hourPx()) * 60;
    const max = Math.max(0, 1440 - d.durationMin);
    const minutes = Math.min(max, Math.max(0, Math.round(raw / SNAP_MINUTES) * SNAP_MINUTES));
    return { ...d, dayTs, minutes };
  }

  // ---------- Mise en page des vues jour et semaine ----------

  /** Place les événements d'un jour ; ceux qui se chevauchent se partagent la largeur. */
  private position(dayEvents: CalendarEvent[], hourPx: number, compactAllowed: boolean): PositionedEvent[] {
    const items = dayEvents.map((event) => {
      const s = new Date(event.start);
      const startMin = Math.min(s.getHours() * 60 + s.getMinutes(), 1440 - SNAP_MINUTES);
      const endMin = Math.min(startMin + durationOf(event), 1440);
      return { event, startMin, endMin, col: 0, cols: 1 };
    });

    let cluster: typeof items = [];
    let columnEnds: number[] = [];
    let clusterEnd = 0;
    const flush = () => {
      cluster.forEach((it) => (it.cols = columnEnds.length));
      cluster = [];
      columnEnds = [];
    };
    for (const it of items) {
      if (cluster.length && it.startMin >= clusterEnd) {
        flush();
      }
      let col = columnEnds.findIndex((end) => end <= it.startMin);
      if (col === -1) {
        col = columnEnds.length;
        columnEnds.push(0);
      }
      columnEnds[col] = it.endMin;
      it.col = col;
      clusterEnd = cluster.length ? Math.max(clusterEnd, it.endMin) : it.endMin;
      cluster.push(it);
    }
    flush();

    return items.map((it) => {
      const height = Math.max(((it.endMin - it.startMin) / 60) * hourPx, MIN_EVENT_PX);
      return {
        event: it.event,
        top: (it.startMin / 60) * hourPx,
        height,
        left: (it.col / it.cols) * 100,
        width: 100 / it.cols,
        compact: compactAllowed && height < COMPACT_PX,
        from: hhmm(it.startMin),
        to: hhmm(it.endMin)
      };
    });
  }
}

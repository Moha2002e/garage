import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ChartData, ChartOptions } from 'chart.js';
import { ChartModule } from 'primeng/chart';
import { Select } from 'primeng/select';
import { formatMoney } from '../../core/format';
import { BrandCount, MonthRevenue, MonthStat, StatusCount } from '../../models/statistics.model';
import { NotificationService } from '../../services/notification.service';
import { SettingsService } from '../../services/settings.service';
import { StatisticsService } from '../../services/statistics.service';

const KEYS = [
  'stats.planned',
  'stats.inProgress',
  'stats.completed',
  'stats.cancelled',
  'stats.xAxis',
  'stats.yAxis',
  'stats.revenue',
  'stats.appointments'
];

// Mêmes teintes que les tags de statut (info, warn, success, danger).
const STATUS_COLORS = {
  PLANNED: '#0ea5e9',
  IN_PROGRESS: '#f97316',
  COMPLETED: '#22c55e',
  CANCELLED: '#ef4444'
} as const;
// Le premier élément est remplacé par la couleur principale choisie.
const BRAND_COLORS = ['#0d9488', '#0ea5e9', '#f97316', '#8b5cf6', '#64748b', '#ec4899', '#eab308', '#14b8a6'];

@Component({
  selector: 'app-statistics',
  imports: [FormsModule, TranslatePipe, ChartModule, Select],
  templateUrl: './statistics.html',
  styleUrl: './statistics.css'
})
export class Statistics {
  private readonly statisticsService = inject(StatisticsService);
  private readonly notifications = inject(NotificationService);
  protected readonly settings = inject(SettingsService);
  private readonly translate = inject(TranslateService);

  private readonly currentYear = new Date().getFullYear();
  // Année en cours et les quatre précédentes.
  protected readonly years = Array.from({ length: 5 }, (_, i) => this.currentYear - i);
  protected readonly year = signal(this.currentYear);
  protected readonly isLoading = signal(false);
  private readonly months = signal<MonthStat[]>([]);
  private readonly statuses = signal<StatusCount[]>([]);
  private readonly revenues = signal<MonthRevenue[]>([]);
  private readonly brands = signal<BrandCount[]>([]);

  // stream() réémet au changement de langue : titres et légendes suivent.
  private readonly labels = toSignal(this.translate.stream(KEYS), {
    initialValue: {} as Record<string, string>
  });

  private readonly monthNames = computed(() => {
    const locale = this.settings.language();
    return Array.from({ length: 12 }, (_, i) =>
      new Date(2000, i, 1).toLocaleDateString(locale, { month: 'short' })
    );
  });

  // Couleurs de texte et de grille lisibles dans les deux thèmes.
  private readonly text = computed(() => (this.settings.theme() === 'dark' ? '#e5e7eb' : '#374151'));
  private readonly grid = computed(() => (this.settings.theme() === 'dark' ? '#374151' : '#e5e7eb'));

  // ---------- Chiffres clés ----------

  private readonly totalAppointments = computed(() => this.statuses().reduce((sum, s) => sum + s.count, 0));
  protected readonly kpis = computed(() => {
    const total = this.totalAppointments();
    const cancelled = this.statuses().find((s) => s.status === 'CANCELLED')?.count ?? 0;
    const rate = total === 0 ? 0 : (cancelled / total) * 100;
    const revenue = this.revenues().reduce((sum, r) => sum + r.revenue, 0);
    return [
      { key: 'stats.kpiTotal', value: String(total) },
      { key: 'stats.kpiCancelRate', value: `${rate.toFixed(1)} %` },
      { key: 'stats.kpiRevenue', value: formatMoney(revenue, this.settings.language()) }
    ];
  });

  // ---------- Courbe : planifiés, en cours, terminés et annulés mois par mois ----------

  protected readonly lineData = computed<ChartData<'line'>>(() => {
    const labels = this.labels() as Record<string, string>;
    const stats = this.months();
    const names = this.monthNames();
    const serie = (key: keyof MonthStat) => names.map((_, i) => stats.find((m) => m.month === i + 1)?.[key] ?? 0);
    const dataset = (label: string, key: keyof MonthStat, color: string) => ({
      label,
      data: serie(key),
      borderColor: color,
      backgroundColor: color,
      tension: 0.25
    });
    return {
      labels: names,
      datasets: [
        dataset(labels['stats.planned'] ?? '', 'planned', STATUS_COLORS.PLANNED),
        dataset(labels['stats.inProgress'] ?? '', 'inProgress', STATUS_COLORS.IN_PROGRESS),
        dataset(labels['stats.completed'] ?? '', 'completed', STATUS_COLORS.COMPLETED),
        dataset(labels['stats.cancelled'] ?? '', 'cancelled', STATUS_COLORS.CANCELLED)
      ]
    };
  });

  protected readonly lineOptions = computed<ChartOptions<'line'>>(() => {
    const labels = this.labels() as Record<string, string>;
    const text = this.text();
    const grid = this.grid();
    return {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: { legend: { position: 'bottom', labels: { color: text } }, tooltip: { enabled: true } },
      scales: {
        x: { title: { display: true, text: labels['stats.xAxis'] ?? '', color: text }, ticks: { color: text }, grid: { color: grid } },
        y: {
          beginAtZero: true,
          title: { display: true, text: labels['stats.yAxis'] ?? '', color: text },
          // Des rendez-vous se comptent en entiers.
          ticks: { color: text, precision: 0 },
          grid: { color: grid }
        }
      }
    };
  });

  // ---------- Anneau : répartition par statut ----------

  protected readonly doughnutData = computed<ChartData<'doughnut'>>(() => {
    const labels = this.labels() as Record<string, string>;
    const order = ['PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'] as const;
    const names: Record<(typeof order)[number], string> = {
      PLANNED: labels['stats.planned'] ?? '',
      IN_PROGRESS: labels['stats.inProgress'] ?? '',
      COMPLETED: labels['stats.completed'] ?? '',
      CANCELLED: labels['stats.cancelled'] ?? ''
    };
    const counts = this.statuses();
    return {
      labels: order.map((s) => names[s]),
      datasets: [
        {
          data: order.map((s) => counts.find((c) => c.status === s)?.count ?? 0),
          backgroundColor: order.map((s) => STATUS_COLORS[s]),
          borderWidth: 0
        }
      ]
    };
  });

  protected readonly doughnutOptions = computed<ChartOptions<'doughnut'>>(() => ({
    responsive: true,
    maintainAspectRatio: false,
    cutout: '62%',
    plugins: { legend: { position: 'bottom', labels: { color: this.text() } } }
  }));

  // ---------- Barres : chiffre d'affaires par mois ----------

  protected readonly revenueData = computed<ChartData<'bar'>>(() => {
    const labels = this.labels() as Record<string, string>;
    const names = this.monthNames();
    const list = this.revenues();
    return {
      labels: names,
      datasets: [
        {
          label: labels['stats.revenue'] ?? '',
          data: names.map((_, i) => list.find((r) => r.month === i + 1)?.revenue ?? 0),
          backgroundColor: this.settings.palette().hex,
          borderRadius: 4
        }
      ]
    };
  });

  protected readonly revenueOptions = computed<ChartOptions<'bar'>>(() => {
    const text = this.text();
    const grid = this.grid();
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { ticks: { color: text }, grid: { display: false } },
        y: { beginAtZero: true, ticks: { color: text }, grid: { color: grid } }
      }
    };
  });

  // ---------- Barres horizontales : marques les plus fréquentes ----------

  protected readonly brandsData = computed<ChartData<'bar'>>(() => {
    const labels = this.labels() as Record<string, string>;
    const list = this.brands();
    return {
      labels: list.map((b) => b.brand),
      datasets: [
        {
          label: labels['stats.appointments'] ?? '',
          data: list.map((b) => b.count),
          backgroundColor: list.map((_, i) => (i === 0 ? this.settings.palette().hex : BRAND_COLORS[i % BRAND_COLORS.length])),
          borderRadius: 4
        }
      ]
    };
  });

  protected readonly brandsOptions = computed<ChartOptions<'bar'>>(() => {
    const text = this.text();
    const grid = this.grid();
    return {
      indexAxis: 'y',
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { beginAtZero: true, ticks: { color: text, precision: 0 }, grid: { color: grid } },
        y: { ticks: { color: text }, grid: { display: false } }
      }
    };
  });

  constructor() {
    this.load();
  }

  protected onYearChange(year: number): void {
    this.year.set(year);
    this.load();
  }

  /** Les quatre séries se chargent séparément : une panne n'efface pas les autres graphiques. */
  private load(): void {
    const year = this.year();
    let pending = 4;
    let notified = false;
    const done = () => {
      pending -= 1;
      if (pending === 0) {
        this.isLoading.set(false);
      }
    };
    const fail = (error: unknown) => {
      if (!notified) {
        notified = true;
        this.notifications.error(error);
      }
      done();
    };
    this.isLoading.set(true);
    this.statisticsService.appointments(year).subscribe({
      next: (r) => { this.months.set(r.months); done(); },
      error: fail
    });
    this.statisticsService.statusDistribution(year).subscribe({
      next: (r) => { this.statuses.set(r); done(); },
      error: fail
    });
    this.statisticsService.revenueByMonth(year).subscribe({
      next: (r) => { this.revenues.set(r); done(); },
      error: fail
    });
    this.statisticsService.topBrands(5).subscribe({
      next: (r) => { this.brands.set(r); done(); },
      error: fail
    });
  }
}

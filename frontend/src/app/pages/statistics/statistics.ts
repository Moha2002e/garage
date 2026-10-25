import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ChartData, ChartOptions } from 'chart.js';
import { ChartModule } from 'primeng/chart';
import { Select } from 'primeng/select';
import { MonthStat } from '../../models/statistics.model';
import { NotificationService } from '../../services/notification.service';
import { SettingsService } from '../../services/settings.service';
import { StatisticsService } from '../../services/statistics.service';

const KEYS = [
  'stats.chartTitle',
  'stats.planned',
  'stats.completed',
  'stats.cancelled',
  'stats.xAxis',
  'stats.yAxis'
];

@Component({
  selector: 'app-statistics',
  imports: [FormsModule, TranslatePipe, ChartModule, Select],
  templateUrl: './statistics.html',
  styleUrl: './statistics.css'
})
export class Statistics {
  private readonly statisticsService = inject(StatisticsService);
  private readonly notifications = inject(NotificationService);
  private readonly settings = inject(SettingsService);
  private readonly translate = inject(TranslateService);

  private readonly currentYear = new Date().getFullYear();
  // Année en cours et les quatre précédentes.
  protected readonly years = Array.from({ length: 5 }, (_, i) => this.currentYear - i);
  protected readonly year = signal(this.currentYear);
  protected readonly isLoading = signal(false);
  private readonly months = signal<MonthStat[]>([]);

  // stream() réémet au changement de langue : titres et légendes suivent.
  private readonly labels = toSignal(this.translate.stream(KEYS), {
    initialValue: {} as Record<string, string>
  });

  // Courbes en lignes : le but est de comparer d'un coup d'oeil planifiés, terminés et annulés mois par mois.
  protected readonly chartData = computed<ChartData<'line'>>(() => {
    const labels = this.labels() as Record<string, string>;
    const stats = this.months();
    const locale = this.settings.language();
    const monthNames = Array.from({ length: 12 }, (_, i) =>
      new Date(2000, i, 1).toLocaleDateString(locale, { month: 'short' })
    );
    const serie = (key: keyof MonthStat) => monthNames.map((_, i) => stats.find((m) => m.month === i + 1)?.[key] ?? 0);
    const dataset = (label: string, key: keyof MonthStat, color: string) => ({
      label,
      data: serie(key),
      borderColor: color,
      backgroundColor: color,
      tension: 0.25
    });
    return {
      labels: monthNames,
      datasets: [
        dataset(labels['stats.planned'] ?? '', 'planned', '#4a6fa5'),
        dataset(labels['stats.completed'] ?? '', 'completed', '#3f8f6b'),
        dataset(labels['stats.cancelled'] ?? '', 'cancelled', '#b5654a')
      ]
    };
  });

  protected readonly chartOptions = computed<ChartOptions<'line'>>(() => {
    const labels = this.labels() as Record<string, string>;
    // Texte lisible dans les deux thèmes.
    const text = this.settings.theme() === 'dark' ? '#e5e7eb' : '#374151';
    const grid = this.settings.theme() === 'dark' ? '#374151' : '#e5e7eb';
    return {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        title: { display: true, text: labels['stats.chartTitle'] ?? '', color: text, font: { size: 16 } },
        legend: { position: 'bottom', labels: { color: text } },
        tooltip: { enabled: true }
      },
      scales: {
        x: {
          title: { display: true, text: labels['stats.xAxis'] ?? '', color: text },
          ticks: { color: text },
          grid: { color: grid }
        },
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

  constructor() {
    this.load();
  }

  protected onYearChange(year: number): void {
    this.year.set(year);
    this.load();
  }

  private load(): void {
    this.isLoading.set(true);
    this.statisticsService.appointments(this.year()).subscribe({
      next: (result) => {
        this.months.set(result.months);
        this.isLoading.set(false);
      },
      error: (error: unknown) => {
        this.isLoading.set(false);
        this.notifications.error(error);
      }
    });
  }
}

import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { Button } from 'primeng/button';
import { Select } from 'primeng/select';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { PALETTES, PaletteId } from '../../core/palettes';
import { LanguageSwitch } from '../../layout/language-switch';
import { DATE_FORMATS } from '../../models/settings.model';
import { AuthService } from '../../services/auth.service';
import { ExportService } from '../../services/export.service';
import { NotificationService } from '../../services/notification.service';
import { SettingsService } from '../../services/settings.service';

@Component({
  selector: 'app-settings',
  imports: [FormsModule, TranslatePipe, Button, Select, ToggleSwitch, LanguageSwitch],
  templateUrl: './settings.html',
  styleUrl: './settings.css'
})
export class Settings {
  private readonly exportService = inject(ExportService);
  private readonly notifications = inject(NotificationService);
  protected readonly settings = inject(SettingsService);
  protected readonly auth = inject(AuthService);

  protected readonly dateFormats = DATE_FORMATS.map((f) => ({ id: f.id, label: f.angular.toUpperCase() }));
  protected readonly palettes = PALETTES;
  protected readonly isExporting = signal(false);

  protected onThemeChange(isDark: boolean): void {
    this.settings.setTheme(isDark ? 'dark' : 'light');
  }

  protected onPaletteChange(id: PaletteId): void {
    this.settings.setPalette(id);
  }

  /** Export réservé à l'ADMIN (le back-end refuse les autres rôles). */
  protected export(kind: 'clients' | 'appointments'): void {
    this.isExporting.set(true);
    const call = kind === 'clients' ? this.exportService.clients() : this.exportService.appointments();
    call.subscribe({
      next: () => {
        this.isExporting.set(false);
        this.notifications.success('settings.exported');
      },
      error: (error: unknown) => {
        this.isExporting.set(false);
        this.notifications.error(error);
      }
    });
  }
}

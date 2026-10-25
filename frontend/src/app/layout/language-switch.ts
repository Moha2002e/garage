import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectButton } from 'primeng/selectbutton';
import { AppLang } from '../models/settings.model';
import { SettingsService } from '../services/settings.service';

/** Choix FR ou EN, disponible dès la page de connexion puis dans le menu. */
@Component({
  selector: 'app-language-switch',
  imports: [FormsModule, SelectButton],
  template: `
    <p-selectbutton
      [options]="options"
      optionLabel="label"
      optionValue="value"
      [allowEmpty]="false"
      size="small"
      ariaLabel="Language"
      [ngModel]="settings.language()"
      (ngModelChange)="settings.setLanguage($event)"
    />
  `
})
export class LanguageSwitch {
  protected readonly settings = inject(SettingsService);
  protected readonly options: { label: string; value: AppLang }[] = [
    { label: 'FR', value: 'fr' },
    { label: 'EN', value: 'en' }
  ];
}

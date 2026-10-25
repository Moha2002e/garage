import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { AppLang, DATE_FORMATS, DateFormatOption, ThemeMode } from '../models/settings.model';

const THEME_KEY = 'garage.theme';
const LANG_KEY = 'garage.language';
const DATE_KEY = 'garage.dateFormat';

/** Préférences d'affichage. Elles restent dans le navigateur : aucun endpoint back-end ne les gère. */
@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly translate = inject(TranslateService);
  private readonly document = inject(DOCUMENT);

  readonly theme = signal<ThemeMode>(localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light');
  readonly language = signal<AppLang>(this.initialLanguage());
  readonly dateFormatId = signal<string>(this.initialDateFormatId());
  readonly dateFormat = computed<DateFormatOption>(
    () => DATE_FORMATS.find((f) => f.id === this.dateFormatId()) ?? DATE_FORMATS[0]
  );

  // ******************************************
  // ************ PERSONNALISÉES ************
  // ******************************************

  /** À appeler une fois au démarrage : applique le thème et la langue mémorisés. */
  init(): void {
    this.applyTheme(this.theme());
    this.translate.setFallbackLang('fr');
    this.translate.use(this.language());
  }

  setTheme(mode: ThemeMode): void {
    this.theme.set(mode);
    localStorage.setItem(THEME_KEY, mode);
    this.applyTheme(mode);
  }

  /** Changement volontaire de l'utilisateur : on le mémorise. */
  setLanguage(lang: AppLang): void {
    this.language.set(lang);
    localStorage.setItem(LANG_KEY, lang);
    this.translate.use(lang);
  }

  /**
   * La langue du compte ne sert que si l'utilisateur n'a jamais choisi de langue sur cet appareil,
   * pour ne pas écraser son choix fait sur la page de connexion.
   */
  applyAccountLanguage(lang: 'FR' | 'EN'): void {
    if (localStorage.getItem(LANG_KEY) === null) {
      const value: AppLang = lang === 'EN' ? 'en' : 'fr';
      this.language.set(value);
      this.translate.use(value);
    }
  }

  setDateFormat(id: string): void {
    this.dateFormatId.set(id);
    localStorage.setItem(DATE_KEY, id);
  }

  private applyTheme(mode: ThemeMode): void {
    // La classe .app-dark est celle que le thème PrimeNG surveille (darkModeSelector).
    this.document.documentElement.classList.toggle('app-dark', mode === 'dark');
  }

  private initialLanguage(): AppLang {
    const saved = localStorage.getItem(LANG_KEY);
    if (saved === 'fr' || saved === 'en') {
      return saved;
    }
    return navigator.language?.toLowerCase().startsWith('en') ? 'en' : 'fr';
  }

  private initialDateFormatId(): string {
    const saved = localStorage.getItem(DATE_KEY);
    return DATE_FORMATS.some((f) => f.id === saved) ? (saved as string) : DATE_FORMATS[0].id;
  }
}

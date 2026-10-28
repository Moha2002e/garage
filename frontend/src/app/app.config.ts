import { provideHttpClient, withInterceptors, withNoXsrfProtection } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { firstValueFrom } from 'rxjs';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { paletteById, readStoredPaletteId } from './core/palettes';
import { apiInterceptor } from './core/http.interceptor';
import { AuthService } from './services/auth.service';
import { SettingsService } from './services/settings.service';

// Palette sobre : gris ardoise pour les surfaces (Aura) et une couleur principale au choix (teal par défaut).
// La palette mémorisée est lue ici pour éviter un flash de la couleur par défaut au démarrage.
const GaragePreset = definePreset(Aura, {
  semantic: {
    primary: paletteById(readStoredPaletteId()).primary
  }
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Le jeton CSRF est ajouté par notre intercepteur (voir http.interceptor.ts), pas par Angular.
    provideHttpClient(withInterceptors([apiInterceptor]), withNoXsrfProtection()),
    providePrimeNG({
      theme: { preset: GaragePreset, options: { darkModeSelector: '.app-dark' } },
      license: environment.primeUiLicense || undefined
    }),
    provideTranslateService({
      fallbackLang: 'fr',
      loader: provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json' })
    }),
    MessageService,
    ConfirmationService,
    // Avant le premier affichage : préférences (thème, langue) puis session via le cookie JWT.
    provideAppInitializer(() => {
      inject(SettingsService).init();
      return firstValueFrom(inject(AuthService).loadSession());
    })
  ]
};

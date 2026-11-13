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

// Surfaces neutres chaudes (ivoire en clair, charbon en sombre) : elles s'accordent à l'or du logo.
const WARM_SURFACE = {
  0: '#ffffff',
  50: '#faf9f6',
  100: '#f4f1ea',
  200: '#e8e3d8',
  300: '#d6cfc0',
  400: '#a79f8e',
  500: '#7a7364',
  600: '#5b5547',
  700: '#403b30',
  800: '#2a2620',
  900: '#1c1915',
  950: '#121110'
};

// Style luxe : or champagne par défaut (couleur principale au choix), surfaces chaudes, angles peu arrondis.
// La palette mémorisée est lue ici pour éviter un flash de la couleur par défaut au démarrage.
const GaragePreset = definePreset(Aura, {
  semantic: {
    primary: paletteById(readStoredPaletteId()).primary,
    content: { borderRadius: '{border.radius.sm}' },
    formField: { borderRadius: '{border.radius.sm}' },
    colorScheme: {
      light: { surface: WARM_SURFACE },
      dark: { surface: WARM_SURFACE }
    }
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

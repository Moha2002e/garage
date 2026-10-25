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
import { routes } from './app.routes';
import { apiInterceptor } from './core/http.interceptor';
import { AuthService } from './services/auth.service';
import { SettingsService } from './services/settings.service';

// Palette sobre : gris ardoise pour les surfaces (Aura) et un vert sapin discret comme couleur principale.
const GaragePreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{teal.50}',
      100: '{teal.100}',
      200: '{teal.200}',
      300: '{teal.300}',
      400: '{teal.400}',
      500: '{teal.600}',
      600: '{teal.700}',
      700: '{teal.800}',
      800: '{teal.900}',
      900: '{teal.950}',
      950: '{teal.950}'
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
      theme: { preset: GaragePreset, options: { darkModeSelector: '.app-dark' } }
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

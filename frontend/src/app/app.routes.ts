import { Routes } from '@angular/router';
import { authGuard, guestGuard, homeGuard, roleGuard } from './core/auth.guard';
import { unsavedChangesGuard } from './core/unsaved-changes.guard';

// Toutes les pages sont chargées à la demande (lazy loading).
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/login/login').then((m) => m.Login)
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/register/register').then((m) => m.Register)
  },
  {
    path: 'forgot-password',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./pages/forgot-password/forgot-password').then((m) => m.ForgotPassword)
  },
  {
    // Ouvert à tous : le lien reçu par e-mail doit marcher même si une session existe.
    path: 'reset-password',
    loadComponent: () => import('./pages/reset-password/reset-password').then((m) => m.ResetPassword)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', canActivate: [homeGuard], children: [] },
      {
        path: 'clients',
        canActivate: [roleGuard('ADMIN', 'EMPLOYEE')],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./pages/clients/clients').then((m) => m.Clients)
      },
      {
        path: 'appointments',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./pages/appointments/appointments').then((m) => m.Appointments)
      },
      {
        path: 'statistics',
        canActivate: [roleGuard('ADMIN', 'EMPLOYEE')],
        loadComponent: () => import('./pages/statistics/statistics').then((m) => m.Statistics)
      },
      {
        path: 'users',
        canActivate: [roleGuard('ADMIN', 'EMPLOYEE')],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./pages/users/users').then((m) => m.Users)
      },
      {
        path: 'settings',
        loadComponent: () => import('./pages/settings/settings').then((m) => m.Settings)
      }
    ]
  },
  // Adresse inconnue : retour à l'accueil, pas de page 404.
  { path: '**', redirectTo: '' }
];

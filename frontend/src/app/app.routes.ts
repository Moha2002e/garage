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
        path: 'dashboard',
        canActivate: [roleGuard('ADMIN', 'EMPLOYEE')],
        loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard)
      },
      {
        path: 'clients',
        canActivate: [roleGuard('ADMIN', 'EMPLOYEE')],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./pages/clients/clients').then((m) => m.Clients)
      },
      {
        path: 'clients/:id',
        canActivate: [roleGuard('ADMIN', 'EMPLOYEE')],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./pages/client-detail/client-detail').then((m) => m.ClientDetail)
      },
      {
        path: 'vehicles',
        canActivate: [roleGuard('ADMIN', 'EMPLOYEE')],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./pages/vehicles/vehicles').then((m) => m.Vehicles)
      },
      {
        path: 'vehicles/:id',
        canActivate: [roleGuard('ADMIN', 'EMPLOYEE')],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./pages/vehicle-detail/vehicle-detail').then((m) => m.VehicleDetail)
      },
      {
        path: 'parts',
        canActivate: [roleGuard('ADMIN', 'EMPLOYEE')],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./pages/parts/parts').then((m) => m.Parts)
      },
      {
        path: 'appointments',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./pages/appointments/appointments').then((m) => m.Appointments)
      },
      { path: 'statistics', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'invoices',
        canActivate: [roleGuard('ADMIN', 'EMPLOYEE')],
        loadComponent: () => import('./pages/invoices/invoices').then((m) => m.Invoices)
      },
      {
        path: 'invoices/errors',
        canActivate: [roleGuard('ADMIN', 'EMPLOYEE')],
        loadComponent: () =>
          import('./pages/invoice-errors/invoice-errors').then((m) => m.InvoiceErrors)
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

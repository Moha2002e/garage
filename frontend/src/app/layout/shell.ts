import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { MenuItem } from 'primeng/api';
import { Button } from 'primeng/button';
import { Menubar } from 'primeng/menubar';
import { merge, startWith } from 'rxjs';
import { Role } from '../models/auth.model';
import { AuthService } from '../services/auth.service';
import { AppLogo } from './app-logo';
import { LanguageSwitch } from './language-switch';

interface MenuEntry {
  key: string;
  icon: string;
  route: string;
  // Rôles autorisés ; absent = tous les rôles connectés.
  roles?: Role[];
}

const ENTRIES: MenuEntry[] = [
  { key: 'menu.dashboard', icon: 'pi pi-home', route: '/dashboard', roles: ['ADMIN', 'EMPLOYEE'] },
  { key: 'menu.clients', icon: 'pi pi-users', route: '/clients', roles: ['ADMIN', 'EMPLOYEE'] },
  { key: 'menu.vehicles', icon: 'pi pi-car', route: '/vehicles', roles: ['ADMIN', 'EMPLOYEE'] },
  { key: 'menu.appointments', icon: 'pi pi-calendar', route: '/appointments' },
  { key: 'menu.invoices', icon: 'pi pi-receipt', route: '/invoices', roles: ['ADMIN', 'EMPLOYEE'] },
  { key: 'menu.users', icon: 'pi pi-id-card', route: '/users', roles: ['ADMIN', 'EMPLOYEE'] },
  { key: 'menu.settings', icon: 'pi pi-cog', route: '/settings' }
];

/** Coque des pages privées : barre de navigation PrimeNG (hamburger sur mobile) qui dépend du rôle, puis la page demandée. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, Button, Menubar, LanguageSwitch, AppLogo],
  templateUrl: './shell.html',
  styleUrl: './shell.css'
})
export class Shell {
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  // Change à chaque (re)chargement de traduction ou changement de langue, pour recalculer les libellés.
  private readonly translations = toSignal(
    merge(this.translate.onLangChange, this.translate.onTranslationChange).pipe(startWith(null))
  );
  protected readonly auth = inject(AuthService);

  // Les options non autorisées ne s'affichent pas.
  protected readonly items = computed<MenuItem[]>(() => {
    this.translations();
    const role = this.auth.role();
    return ENTRIES.filter((e) => !e.roles || (role !== null && e.roles.includes(role))).map((e) => ({
      label: this.translate.instant(e.key),
      icon: e.icon,
      routerLink: e.route
    }));
  });

  protected onLogout(): void {
    // Qu'il réussisse ou non, on quitte l'interface privée.
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: () => {
        this.auth.clearSession();
        this.router.navigateByUrl('/login');
      }
    });
  }
}

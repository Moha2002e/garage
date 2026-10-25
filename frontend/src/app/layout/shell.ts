import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterOutlet } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { MenuItem } from 'primeng/api';
import { Button } from 'primeng/button';
import { PanelMenu } from 'primeng/panelmenu';
import { Role } from '../models/auth.model';
import { AuthService } from '../services/auth.service';
import { LanguageSwitch } from './language-switch';

interface MenuEntry {
  key: string;
  icon: string;
  route: string;
  // Rôles autorisés ; absent = tous les rôles connectés.
  roles?: Role[];
}

const ENTRIES: MenuEntry[] = [
  { key: 'menu.clients', icon: 'pi pi-users', route: '/clients', roles: ['ADMIN', 'EMPLOYEE'] },
  { key: 'menu.appointments', icon: 'pi pi-calendar', route: '/appointments' },
  { key: 'menu.statistics', icon: 'pi pi-chart-line', route: '/statistics', roles: ['ADMIN', 'EMPLOYEE'] },
  { key: 'menu.users', icon: 'pi pi-id-card', route: '/users', roles: ['ADMIN', 'EMPLOYEE'] },
  { key: 'menu.settings', icon: 'pi pi-cog', route: '/settings' }
];

/** Coque des pages privées : menu latéral qui dépend du rôle, puis la page demandée. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, TranslatePipe, PanelMenu, Button, LanguageSwitch],
  templateUrl: './shell.html',
  styleUrl: './shell.css'
})
export class Shell {
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  // stream() réémet quand la langue change, donc le menu se retraduit seul.
  private readonly labels = toSignal(this.translate.stream(ENTRIES.map((e) => e.key)), {
    initialValue: {} as Record<string, string>
  });

  protected readonly menuItems = computed<MenuItem[]>(() => {
    const role = this.auth.role();
    const labels = this.labels() as Record<string, string>;
    // Les options non autorisées ne s'affichent pas.
    return ENTRIES.filter((e) => !e.roles || (role !== null && e.roles.includes(role))).map((e) => ({
      label: labels[e.key] ?? e.key,
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

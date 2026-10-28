import { Component, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Button } from 'primeng/button';

/** En-tête des pages détail : bouton retour et fil d'Ariane (liste parente puis élément courant). */
@Component({
  selector: 'app-detail-header',
  imports: [RouterLink, TranslatePipe, Button],
  template: `
    <div class="detail-header">
      <p-button
        icon="pi pi-arrow-left"
        [label]="'common.back' | translate"
        severity="secondary"
        [outlined]="true"
        (onClick)="back()"
      />
      <nav class="breadcrumb" [attr.aria-label]="'common.breadcrumb' | translate">
        <a [routerLink]="parentRoute()">{{ parentKey() | translate }}</a>
        <i class="pi pi-angle-right" aria-hidden="true"></i>
        <span aria-current="page">{{ current() }}</span>
      </nav>
    </div>
  `,
  styles: `
    .detail-header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem 1rem;
    }
    .breadcrumb {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
      min-width: 0;
      color: var(--p-text-muted-color);
    }
    .breadcrumb a {
      color: var(--p-primary-color);
      text-decoration: none;
    }
    .breadcrumb a:hover {
      text-decoration: underline;
    }
    .breadcrumb span {
      color: var(--p-text-color);
      font-weight: 600;
      overflow-wrap: anywhere;
    }
    .breadcrumb i {
      font-size: 0.75rem;
    }
  `
})
export class DetailHeader {
  private readonly router = inject(Router);

  /** Clé de traduction de la liste parente (ex. menu.clients). */
  readonly parentKey = input.required<string>();
  readonly parentRoute = input.required<string>();
  /** Nom de l'élément affiché, vide pendant le chargement. */
  readonly current = input<string>('');

  protected back(): void {
    this.router.navigateByUrl(this.parentRoute());
  }
}

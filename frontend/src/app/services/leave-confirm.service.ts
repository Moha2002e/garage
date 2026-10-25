import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from 'primeng/api';

/** Fenêtre de confirmation quand on quitte un formulaire modifié (p-confirmdialog global). */
@Injectable({ providedIn: 'root' })
export class LeaveConfirmService {
  private readonly confirmation = inject(ConfirmationService);
  private readonly translate = inject(TranslateService);

  // ******************************************
  // ************ PERSONNALISÉES ************
  // ******************************************

  /** Résout à true si l'utilisateur accepte de perdre ses modifications. */
  confirmLeave(): Promise<boolean> {
    return new Promise((resolve) => {
      this.confirmation.confirm({
        header: this.translate.instant('leave.title'),
        message: this.translate.instant('leave.message'),
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: this.translate.instant('common.leave'),
        rejectLabel: this.translate.instant('common.stay'),
        accept: () => resolve(true),
        reject: () => resolve(false)
      });
    });
  }
}

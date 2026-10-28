import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { LeaveConfirmService } from '../services/leave-confirm.service';

/** Les pages avec un formulaire dans une fenêtre de dialogue implémentent cette interface. */
export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

/** Évite de perdre une saisie en changeant de page : on demande confirmation seulement si le formulaire est modifié. */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component) => {
  if (!component.hasUnsavedChanges()) {
    return true;
  }
  return inject(LeaveConfirmService).confirmLeave();
};

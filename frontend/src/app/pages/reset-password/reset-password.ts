import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';
import { LanguageSwitch } from '../../layout/language-switch';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';

/** Vérifie que les deux mots de passe saisis sont identiques. */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  return group.get('newPassword')?.value === group.get('confirmPassword')?.value
    ? null
    : { mismatch: true };
}

@Component({
  selector: 'app-reset-password',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    Button,
    Card,
    Message,
    Password,
    LanguageSwitch
  ],
  templateUrl: './reset-password.html',
  styleUrl: './reset-password.css'
})
export class ResetPassword {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  // Le jeton vient du lien reçu par e-mail : /reset-password?token=...
  protected readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');

  protected readonly resetForm = this.fb.group(
    {
      newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
      confirmPassword: ['', Validators.required]
    },
    { validators: passwordsMatch }
  );
  protected readonly isSubmitting = signal(false);
  protected readonly submitError = signal<string | null>(null);

  protected onSubmit(): void {
    this.submitError.set(null);
    if (this.resetForm.invalid || !this.token) {
      this.resetForm.markAllAsTouched();
      this.submitError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    this.isSubmitting.set(true);
    this.resetForm.disable();
    this.auth
      .resetPassword({ token: this.token, newPassword: this.resetForm.getRawValue().newPassword })
      .subscribe({
        next: () => {
          this.notifications.success('reset.success');
          this.router.navigateByUrl('/login');
        },
        error: (error: unknown) => {
          this.submitError.set(this.notifications.errorText(error));
          this.resetForm.enable();
          this.isSubmitting.set(false);
        }
      });
  }
}

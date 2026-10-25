import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { LanguageSwitch } from '../../layout/language-switch';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-forgot-password',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    Button,
    Card,
    InputText,
    Message,
    LanguageSwitch
  ],
  templateUrl: './forgot-password.html',
  styleUrl: './forgot-password.css'
})
export class ForgotPassword {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  protected readonly forgotForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]]
  });
  protected readonly isSubmitting = signal(false);
  protected readonly isSent = signal(false);
  protected readonly submitError = signal<string | null>(null);

  protected onSubmit(): void {
    this.submitError.set(null);
    if (this.forgotForm.invalid) {
      this.forgotForm.markAllAsTouched();
      this.submitError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    this.isSubmitting.set(true);
    this.forgotForm.disable();
    this.auth.forgotPassword(this.forgotForm.getRawValue().email).subscribe({
      // Le back-end répond pareil que l'e-mail existe ou non : on affiche donc toujours le même message.
      next: () => {
        this.isSent.set(true);
        this.isSubmitting.set(false);
      },
      error: (error: unknown) => {
        this.submitError.set(this.notifications.errorText(error));
        this.forgotForm.enable();
        this.isSubmitting.set(false);
      }
    });
  }
}

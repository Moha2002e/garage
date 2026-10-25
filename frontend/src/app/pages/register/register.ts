import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';
import { LanguageSwitch } from '../../layout/language-switch';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { SettingsService } from '../../services/settings.service';

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    Button,
    Card,
    InputText,
    Message,
    Password,
    LanguageSwitch
  ],
  templateUrl: './register.html',
  styleUrl: './register.css'
})
export class Register {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly settings = inject(SettingsService);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  protected readonly registerForm = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]]
  });
  protected readonly isSubmitting = signal(false);
  protected readonly submitError = signal<string | null>(null);

  protected onSubmit(): void {
    this.submitError.set(null);
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      this.submitError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    this.isSubmitting.set(true);
    this.registerForm.disable();
    // Pas de choix de rôle : le back-end crée toujours un compte CLIENT.
    // La langue envoyée est celle choisie sur cet appareil.
    const language = this.settings.language() === 'en' ? 'EN' : 'FR';
    this.auth.register({ ...this.registerForm.getRawValue(), language }).subscribe({
      next: () => {
        this.notifications.success('register.success');
        this.router.navigateByUrl('/login');
      },
      error: (error: unknown) => {
        this.submitError.set(this.notifications.errorText(error));
        this.registerForm.enable();
        this.isSubmitting.set(false);
      }
    });
  }
}

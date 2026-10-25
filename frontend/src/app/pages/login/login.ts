import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { Checkbox } from 'primeng/checkbox';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';
import { LanguageSwitch } from '../../layout/language-switch';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    Button,
    Card,
    Checkbox,
    InputText,
    Message,
    Password,
    LanguageSwitch
  ],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  protected readonly loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
    rememberMe: [false]
  });
  protected readonly isSubmitting = signal(false);
  protected readonly submitError = signal<string | null>(null);

  protected onSubmit(): void {
    this.submitError.set(null);
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      this.submitError.set(this.translate.instant('errors.formInvalid'));
      return;
    }
    // Formulaire et bouton bloqués pendant l'envoi pour éviter le double clic.
    this.isSubmitting.set(true);
    this.loginForm.disable();
    this.auth.login(this.loginForm.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl(this.auth.homePath()),
      error: (error: unknown) => {
        this.submitError.set(this.notifications.errorText(error));
        this.loginForm.enable();
        this.isSubmitting.set(false);
      }
    });
  }
}

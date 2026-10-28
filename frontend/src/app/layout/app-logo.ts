import { Component } from '@angular/core';

/** Logo du garage dessiné en ligne : la couleur de fond suit la couleur principale choisie. */
@Component({
  selector: 'app-logo',
  template: `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" role="img" aria-label="Garage">
      <rect width="64" height="64" rx="14" style="fill: var(--p-primary-600, #0f766e)" />
      <g fill="#ffffff">
        <circle cx="32" cy="32" r="19" />
        <rect x="28.5" y="7" width="7" height="12" rx="1.5" />
        <rect x="28.5" y="7" width="7" height="12" rx="1.5" transform="rotate(45 32 32)" />
        <rect x="28.5" y="7" width="7" height="12" rx="1.5" transform="rotate(90 32 32)" />
        <rect x="28.5" y="7" width="7" height="12" rx="1.5" transform="rotate(135 32 32)" />
        <rect x="28.5" y="7" width="7" height="12" rx="1.5" transform="rotate(180 32 32)" />
        <rect x="28.5" y="7" width="7" height="12" rx="1.5" transform="rotate(225 32 32)" />
        <rect x="28.5" y="7" width="7" height="12" rx="1.5" transform="rotate(270 32 32)" />
        <rect x="28.5" y="7" width="7" height="12" rx="1.5" transform="rotate(315 32 32)" />
      </g>
      <circle cx="32" cy="32" r="13" style="fill: var(--p-primary-600, #0f766e)" />
      <path
        d="M37.7 26.3 A8 8 0 1 0 40 32 H33"
        fill="none"
        stroke="#ffffff"
        stroke-width="3.6"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
  `,
  styles: `
    :host {
      display: block;
    }
    svg {
      display: block;
      width: 100%;
      height: 100%;
    }
  `
})
export class AppLogo {}

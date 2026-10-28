/** Montant en euros, selon la langue active (le format de devise n'est pas réglable dans l'application). */
export function formatMoney(value: number | null | undefined, lang: string): string {
  return new Intl.NumberFormat(lang === 'en' ? 'en-GB' : 'fr-BE', {
    style: 'currency',
    currency: 'EUR'
  }).format(value ?? 0);
}

/** Durée lisible : 45 min, 1 h, 1 h 30. */
export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) {
    return `${rest} min`;
  }
  return rest === 0 ? `${hours} h` : `${hours} h ${String(rest).padStart(2, '0')}`;
}

/** Durée d'une exécution : 850 ms, 12 s, 1 min 05 s. Vide tant que l'exécution n'est pas finie. */
export function formatRunDuration(start: string, end: string | null): string {
  if (!end) {
    return '';
  }
  const ms = new Date(end).getTime() - new Date(start).getTime();
  if (Number.isNaN(ms) || ms < 0) {
    return '';
  }
  if (ms < 1000) {
    return `${ms} ms`;
  }
  const seconds = Math.round(ms / 1000);
  if (seconds < 60) {
    return `${seconds} s`;
  }
  return `${Math.floor(seconds / 60)} min ${String(seconds % 60).padStart(2, '0')} s`;
}

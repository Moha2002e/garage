/** Palettes de couleur principale. Les couleurs de statut (vert, rouge, orange, bleu) ne dépendent jamais de ce choix. */
export type PaletteId = 'teal' | 'blue' | 'indigo' | 'green' | 'orange' | 'rose';

export interface Palette {
  id: PaletteId;
  // Teinte de référence (nuance 600) : pastille d'aperçu et couleur des graphiques.
  hex: string;
  // Échelle appliquée au preset PrimeNG : la nuance 500 du thème prend la 600 de la palette (texte blanc lisible).
  primary: Record<string, string>;
}

function scale(name: string): Record<string, string> {
  return {
    50: `{${name}.50}`,
    100: `{${name}.100}`,
    200: `{${name}.200}`,
    300: `{${name}.300}`,
    400: `{${name}.400}`,
    500: `{${name}.600}`,
    600: `{${name}.700}`,
    700: `{${name}.800}`,
    800: `{${name}.900}`,
    900: `{${name}.950}`,
    950: `{${name}.950}`
  };
}

export const PALETTES: Palette[] = [
  { id: 'teal', hex: '#0d9488', primary: scale('teal') },
  { id: 'blue', hex: '#2563eb', primary: scale('blue') },
  { id: 'indigo', hex: '#4f46e5', primary: scale('indigo') },
  { id: 'green', hex: '#16a34a', primary: scale('green') },
  { id: 'orange', hex: '#ea580c', primary: scale('orange') },
  { id: 'rose', hex: '#e11d48', primary: scale('rose') }
];

export const DEFAULT_PALETTE_ID: PaletteId = 'teal';
export const PALETTE_KEY = 'garage.palette';

export function paletteById(id: string | null | undefined): Palette {
  return PALETTES.find((p) => p.id === id) ?? PALETTES[0];
}

/** Lecture protégée : le stockage peut être indisponible (navigation privée, données bloquées). */
export function readStoredPaletteId(): PaletteId {
  try {
    return paletteById(localStorage.getItem(PALETTE_KEY)).id;
  } catch {
    return DEFAULT_PALETTE_ID;
  }
}

export function storePaletteId(id: PaletteId): void {
  try {
    localStorage.setItem(PALETTE_KEY, id);
  } catch {
    // Le choix reste appliqué pour la session, sans être mémorisé.
  }
}

/** Palettes de couleur principale. Les couleurs de statut (vert, rouge, orange, bleu) ne dépendent jamais de ce choix. */
export type PaletteId = 'gold' | 'teal' | 'blue' | 'indigo' | 'green' | 'orange' | 'rose';

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

/**
 * Or champagne. Échelle écrite à la main (pas de nuance « gold » dans PrimeNG) : la 500 est un or bronze assez
 * sombre pour garder un texte blanc lisible (contraste 4,9) sur les boutons ; la 400, plus claire, sert en mode sombre.
 */
const GOLD: Record<string, string> = {
  50: '#faf6ec',
  100: '#f2e8cc',
  200: '#e6d29e',
  300: '#d9ba72',
  400: '#d4ae5c',
  500: '#8f6a1f',
  600: '#79591a',
  700: '#634816',
  800: '#4f3a13',
  900: '#3f2e10',
  950: '#2a1f0b'
};

export const PALETTES: Palette[] = [
  { id: 'gold', hex: '#b8913f', primary: GOLD },
  { id: 'teal', hex: '#0d9488', primary: scale('teal') },
  { id: 'blue', hex: '#2563eb', primary: scale('blue') },
  { id: 'indigo', hex: '#4f46e5', primary: scale('indigo') },
  { id: 'green', hex: '#16a34a', primary: scale('green') },
  { id: 'orange', hex: '#ea580c', primary: scale('orange') },
  { id: 'rose', hex: '#e11d48', primary: scale('rose') }
];

export const DEFAULT_PALETTE_ID: PaletteId = 'gold';
// Nouvelle clé (v2) : les choix mémorisés avec l'ancien style (teal, indigo…) sont abandonnés une fois, pour que tout le monde voie l'or.
export const PALETTE_KEY = 'garage.palette.v2';

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

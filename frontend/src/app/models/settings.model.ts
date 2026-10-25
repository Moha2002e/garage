export type ThemeMode = 'light' | 'dark';
export type AppLang = 'fr' | 'en';

export interface DateFormatOption {
  id: string;
  // Format du pipe date d'Angular.
  angular: string;
  // Format du p-datepicker (syntaxe PrimeNG).
  prime: string;
}

export const DATE_FORMATS: DateFormatOption[] = [
  { id: 'eu', angular: 'dd/MM/yyyy', prime: 'dd/mm/yy' },
  { id: 'us', angular: 'MM/dd/yyyy', prime: 'mm/dd/yy' },
  { id: 'iso', angular: 'yyyy-MM-dd', prime: 'yy-mm-dd' }
];

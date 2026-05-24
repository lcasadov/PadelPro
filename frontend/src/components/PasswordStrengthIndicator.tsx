// PasswordStrengthIndicator — validación visual en tiempo real para el formulario de registro.
// Muestra tres indicadores: longitud mínima, mayúscula y número.
import styles from './PasswordStrengthIndicator.module.css';

interface PasswordStrengthIndicatorProps {
  password: string;
}

interface Rule {
  label: string;
  test: (p: string) => boolean;
}

const RULES: Rule[] = [
  { label: 'Mínimo 8 caracteres', test: (p) => p.length >= 8 },
  { label: '1 letra mayúscula', test: (p) => /[A-Z]/.test(p) },
  { label: '1 número', test: (p) => /[0-9]/.test(p) },
];

export function PasswordStrengthIndicator({ password }: PasswordStrengthIndicatorProps) {
  if (!password) return null;

  return (
    <ul className={styles.list} aria-label="Requisitos de contraseña">
      {RULES.map((rule) => {
        const passed = rule.test(password);
        return (
          <li
            key={rule.label}
            className={passed ? styles.passed : styles.failed}
            aria-live="polite"
          >
            <span className={styles.icon} aria-hidden="true">
              {passed ? '✓' : '✗'}
            </span>
            {rule.label}
          </li>
        );
      })}
    </ul>
  );
}

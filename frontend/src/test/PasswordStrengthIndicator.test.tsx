import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PasswordStrengthIndicator } from '../components/PasswordStrengthIndicator';

describe('PasswordStrengthIndicator', () => {
  it('renders nothing when password is empty', () => {
    const { container } = render(<PasswordStrengthIndicator password="" />);
    expect(container.firstChild).toBeNull();
  });

  it('shows all rules as failed for a weak password', () => {
    render(<PasswordStrengthIndicator password="abc" />);
    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(3);
    items.forEach((item) => {
      expect(item.textContent).toContain('✗');
    });
  });

  it('shows length rule as passed for password >= 8 chars', () => {
    render(<PasswordStrengthIndicator password="abcdefgh" />);
    const lengthItem = screen.getByText(/Mínimo 8 caracteres/);
    expect(lengthItem.textContent).toContain('✓');
  });

  it('shows uppercase rule as passed when password has uppercase', () => {
    render(<PasswordStrengthIndicator password="Abcdefgh" />);
    const uppercaseItem = screen.getByText(/1 letra mayúscula/);
    expect(uppercaseItem.textContent).toContain('✓');
  });

  it('shows number rule as passed when password has a digit', () => {
    render(<PasswordStrengthIndicator password="abcdefg1" />);
    const numberItem = screen.getByText(/1 número/);
    expect(numberItem.textContent).toContain('✓');
  });

  it('shows all rules as passed for a strong password', () => {
    render(<PasswordStrengthIndicator password="Abcdefg1" />);
    const items = screen.getAllByRole('listitem');
    items.forEach((item) => {
      expect(item.textContent).toContain('✓');
    });
  });
});

// 7.1 — ForgotPasswordPage (TDD RED): mensaje "contacta con el administrador".
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage';

describe('ForgotPasswordPage', () => {
  it('tells the user to contact the club administrator', () => {
    render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>
    );
    expect(screen.getByText(/administrador del club/i)).toBeDefined();
  });

  it('offers a link back to login', () => {
    render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>
    );
    const link = screen.getByRole('link', { name: /inicio de sesión|iniciar sesión|volver/i });
    expect(link.getAttribute('href')).toBe('/login');
  });
});

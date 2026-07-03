// 4.1 (no circular) — UsuarioFormModal valida el formato del email en el
// cliente ANTES de enviar. Reemplaza el test circular de adminUsuariosApi
// (que solo comprobaba que axios propaga un 400 mockeado por MSW) por uno que
// ejerce el comportamiento real del componente: con un email mal formado NO se
// dispara onSubmit y se muestra el error; con un email válido sí.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { UsuarioFormModal } from '../components/UsuarioFormModal';

function renderModal(mode: 'create' | 'edit', onSubmit = vi.fn()) {
  const initial =
    mode === 'edit'
      ? { id: 5, firstName: 'Leo', lastName: 'Paz', email: 'leo@test.com', phone: '600333444', status: 'ACTIVE', role: 'USER' }
      : null;
  render(
    <UsuarioFormModal mode={mode} initial={initial} onSubmit={onSubmit} onClose={vi.fn()} />
  );
  const dialog = screen.getByRole('dialog');
  return { dialog, onSubmit };
}

describe('UsuarioFormModal — validación de email (no circular)', () => {
  it('edición: email mal formado NO llama a onSubmit y muestra el error', async () => {
    const { dialog, onSubmit } = renderModal('edit');

    const email = within(dialog).getByLabelText(/email/i);
    await userEvent.clear(email);
    await userEvent.type(email, 'no-es-email');
    await userEvent.click(within(dialog).getByRole('button', { name: /guardar|actualizar/i }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(await within(dialog).findByText(/inv|formato|válid/i)).toBeDefined();
  });

  it('edición: email bien formado SÍ llama a onSubmit con los valores', async () => {
    const { dialog, onSubmit } = renderModal('edit');

    const email = within(dialog).getByLabelText(/email/i);
    await userEvent.clear(email);
    await userEvent.type(email, 'leo2@test.com');
    await userEvent.click(within(dialog).getByRole('button', { name: /guardar|actualizar/i }));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ email: 'leo2@test.com', firstName: 'Leo' })
    );
  });

  it('alta: email mal formado NO llama a onSubmit y muestra el error', async () => {
    const { dialog, onSubmit } = renderModal('create');

    await userEvent.type(within(dialog).getByLabelText(/nombre/i), 'Nuevo');
    await userEvent.type(within(dialog).getByLabelText(/apellidos?/i), 'Socio');
    await userEvent.type(within(dialog).getByLabelText(/email/i), 'sin-arroba');
    await userEvent.click(within(dialog).getByRole('button', { name: /crear|guardar|dar de alta/i }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(await within(dialog).findByText(/inv|formato|válid/i)).toBeDefined();
  });
});

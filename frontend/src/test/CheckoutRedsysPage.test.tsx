// pagos-redsys-online (Grupo 6, D6) — CheckoutRedsysPage — TDD.
// Dado el response de iniciar pago (en el state de navegación), la página construye
// el form firmado con los tres campos ds* y el action = redsysUrl, y hace auto-submit
// (POST) al TPV. El submit se mockea (jsdom no implementa form.submit y no queremos
// navegar de verdad en test). Sin state → mensaje de error + volver a mis reservas.
import { render, screen } from '@testing-library/react';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { CheckoutRedsysPage } from '../pages/CheckoutRedsysPage';

const pago = {
  pagoId: 'p-1',
  redsysOrderId: '0001abc',
  redsysUrl: 'https://sis-t.redsys.es:25443/sis/realizarPago',
  amount: 16,
  status: 'IN_PROGRESS' as const,
  dsSignatureVersion: 'HMAC_SHA256_V1',
  dsMerchantParameters: 'eyJEc19NZXJjaGFudF9BbW91bnQiOiIxNjAwIn0=',
  dsSignature: 'firma-base64-abc',
};

function renderWithState(state: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/pagos/checkout', state }]}>
      <Routes>
        <Route path="/pagos/checkout" element={<CheckoutRedsysPage />} />
        <Route path="/reservas/mias" element={<div>Mis reservas</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('CheckoutRedsysPage', () => {
  afterEach(() => vi.restoreAllMocks());

  it('construye el form firmado (3 campos ds*) con action = redsysUrl y hace auto-submit', () => {
    const submitSpy = vi
      .spyOn(HTMLFormElement.prototype, 'submit')
      .mockImplementation(() => {});

    renderWithState({ pago });

    const form = screen.getByTestId('redsys-form') as HTMLFormElement;
    // POST al TPV con la URL firmada.
    expect(form.getAttribute('method')?.toUpperCase()).toBe('POST');
    expect(form.getAttribute('action')).toBe(pago.redsysUrl);

    // Los tres campos firmados con el protocolo Redsys.
    const version = form.querySelector('input[name="Ds_SignatureVersion"]') as HTMLInputElement;
    const params = form.querySelector('input[name="Ds_MerchantParameters"]') as HTMLInputElement;
    const signature = form.querySelector('input[name="Ds_Signature"]') as HTMLInputElement;
    expect(version.value).toBe(pago.dsSignatureVersion);
    expect(params.value).toBe(pago.dsMerchantParameters);
    expect(signature.value).toBe(pago.dsSignature);

    // Auto-submit disparado al montar.
    expect(submitSpy).toHaveBeenCalledTimes(1);

    // Datos de tarjeta NUNCA en PadelPro (RN-PAY-03): no hay inputs de PAN/CVV.
    expect(form.querySelector('input[name*="tarjeta" i]')).toBeNull();
    expect(form.querySelector('input[name*="cvv" i]')).toBeNull();
  });

  it('muestra el importe a pagar', () => {
    vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(() => {});
    renderWithState({ pago });
    expect(screen.getByText(/16,00\s*€/)).toBeInTheDocument();
  });

  it('sin datos de pago en el state muestra error y enlace a mis reservas, sin submit', () => {
    const submitSpy = vi
      .spyOn(HTMLFormElement.prototype, 'submit')
      .mockImplementation(() => {});

    renderWithState(null);

    expect(screen.getByText(/pago no disponible/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /mis reservas/i })).toBeInTheDocument();
    expect(submitSpy).not.toHaveBeenCalled();
  });
});

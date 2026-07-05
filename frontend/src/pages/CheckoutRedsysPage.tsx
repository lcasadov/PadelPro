// pagos-redsys-online (Grupo 6, D6) — CheckoutRedsysPage (mockup 11).
// Recibe el response de `iniciarPago` en el `state` de navegación y construye un
// formulario oculto con los tres campos firmados (Ds_SignatureVersion /
// Ds_MerchantParameters / Ds_Signature) que auto-envía (POST) al `redsysUrl` del
// TPV de Redsys. Los datos de tarjeta NUNCA pasan por PadelPro (RN-PAY-03): el
// navegador se redirige al entorno seguro de Redsys y allí introduce la tarjeta.
import { useEffect, useRef } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { type IniciarPagoResponse } from '../services/pagosApi';
import { reservasPaths } from './reservasPaths';
import './pages.css';
import styles from './CheckoutRedsysPage.module.css';

interface CheckoutLocationState {
  pago?: IniciarPagoResponse;
}

/** Formatea el importe como euros. El resto de la app trata `amount`/`priceTotal`
 *  en euros; el importe autoritativo en céntimos viaja dentro de Ds_MerchantParameters. */
function formatPrecio(amount: number): string {
  return new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(amount);
}

export function CheckoutRedsysPage() {
  const location = useLocation();
  const pago = (location.state as CheckoutLocationState | null)?.pago;
  const formRef = useRef<HTMLFormElement>(null);

  // Auto-submit al TPV en cuanto el form está montado con los parámetros firmados.
  useEffect(() => {
    if (pago && formRef.current) {
      formRef.current.submit();
    }
  }, [pago]);

  // Sin datos de pago (p. ej. recarga directa de la URL): no hay nada que enviar.
  if (!pago) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <h1 className={styles.title}>Pago no disponible</h1>
          <p className={styles.notice}>
            No se ha podido iniciar el pago. Vuelve a tus reservas e inténtalo de nuevo.
          </p>
          <Link to={reservasPaths.mias} className="p-btn p-btn-primary">
            Volver a mis reservas
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.merch}>
          <span className={styles.merchLogo}>PP</span>
          <div className={styles.merchInfo}>
            <span className={styles.merchName}>PadelPro</span>
            <span className={styles.merchUrl}>checkout.redsys.es</span>
          </div>
          <span className={styles.secure} aria-hidden="true">
            🔒 3DSv2
          </span>
        </div>

        <div className={styles.amountBlock}>
          <span className={styles.amountLab}>Importe</span>
          <span className={styles.amount}>{formatPrecio(pago.amount)}</span>
          <span className={styles.orderRef}>Pedido {pago.redsysOrderId}</span>
        </div>

        <div className={styles.spinner} role="status" aria-label="Redirigiendo a Redsys" />
        <p className={styles.notice}>
          Te estamos redirigiendo a la pasarela de pago segura de Redsys. Introduce los
          datos de tu tarjeta allí; nunca se almacenan en PadelPro.
        </p>

        {/* Form firmado que se auto-envía al TPV. Sin datos de tarjeta (RN-PAY-03). */}
        <form ref={formRef} method="POST" action={pago.redsysUrl} data-testid="redsys-form">
          <input type="hidden" name="Ds_SignatureVersion" value={pago.dsSignatureVersion} />
          <input type="hidden" name="Ds_MerchantParameters" value={pago.dsMerchantParameters} />
          <input type="hidden" name="Ds_Signature" value={pago.dsSignature} />
          <button type="submit" className="p-btn p-btn-primary">
            Continuar al pago →
          </button>
        </form>

        <p className={styles.footerTech}>Procesado por Redsys · HMAC SHA-256 · PCI-DSS L1</p>
      </div>
    </div>
  );
}

# E2E smoke tests (Playwright)

Tests end-to-end del journey crítico de PadelPro (**login → crear reserva**) en un
navegador real contra el **stack completo** (frontend build + backend + postgres),
sin mocks. Red de seguridad que complementa los tests de componente (vitest + MSW).

Change OpenSpec: `e2e-smoke-tests` (Issue #205).

## Ejecutar en local

Desde la raíz del repo, levanta el stack con la BD sembrada y el health de correo
desactivado (evita que un SMTP sin configurar marque el backend DOWN):

```bash
MANAGEMENT_HEALTH_MAIL_ENABLED=false \
ADMIN_EMAIL=e2e-admin@padelpro.local \
ADMIN_PASSWORD='E2ePassw0rd!secure' \
docker compose up -d --build --wait
```

Luego, en `e2e/`:

```bash
cd e2e
npm ci
npx playwright install chromium
npx playwright test          # o: npm run test:headed para verlo
```

Al terminar:

```bash
docker compose down -v       # destruye la BD de prueba
```

## Notas

- **BD sembrada:** el backend crea un ADMIN ACTIVE de bootstrap con
  `ADMIN_EMAIL`/`ADMIN_PASSWORD`. El test inicia sesión con ese usuario
  (`/api/reservas` solo exige autenticación, así que el ADMIN puede reservar).
- **Fecha futura:** el smoke reserva para *mañana* porque el backend rechaza
  franjas en el pasado.
- **Navegación por UI, no `page.goto`:** el access token vive solo en memoria
  (RN-AUTH-09); una recarga completa cerraría la sesión. El test navega con los
  controles de la app (routing cliente).
- **No reproduce el secure-context de #201:** `localhost` siempre es *secure
  context*; para ese ángulo haría falta un post-deploy smoke contra el HTTPS real
  (follow-up de `tls-https-ec2`). Aun así, este smoke sí caza cualquier ruptura del
  journey por contrato/UI/backend.
- **Config:** `E2E_BASE_URL` (default `http://localhost:5173`),
  `E2E_ADMIN_EMAIL`/`E2E_ADMIN_PASSWORD`.

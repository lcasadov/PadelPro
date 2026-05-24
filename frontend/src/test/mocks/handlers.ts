// MSW default handlers — overridden per-test via server.use(...)
import { http, HttpResponse } from 'msw';

export const handlers = [
  http.post('/api/auth/login', () =>
    HttpResponse.json(
      { access_token: 'test.jwt.token', token_type: 'Bearer', expires_in: 900 },
      { status: 200 }
    )
  ),
  http.post('/api/auth/register', () =>
    HttpResponse.json(
      { id: 1, email: 'user@test.com', role: 'USER' },
      { status: 201 }
    )
  ),
];

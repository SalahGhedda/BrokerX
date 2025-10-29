import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '3m', target: 200 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<900'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const ACCOUNT_ID = __ENV.ACCOUNT_ID || '00000000-0000-0000-0000-000000000000';
const TOKEN = __ENV.TOKEN || '';

const params = TOKEN
  ? {
      headers: {
        Authorization: `Bearer ${TOKEN}`,
        'Content-Type': 'application/json',
      },
    }
  : { headers: { 'Content-Type': 'application/json' } };

export default function () {
  const summary = http.get(`${BASE_URL}/accounts/${ACCOUNT_ID}/summary`, params);
  check(summary, {
    'summary status is 200': (r) => r.status === 200,
  });

  const clientOrderId = uuidv4();
  const limitPrice = (150 + Math.random() * 5).toFixed(2);
  const payload = JSON.stringify({
    symbol: 'AAPL',
    side: 'BUY',
    type: 'LIMIT',
    quantity: 1,
    limitPrice: Number(limitPrice),
    clientOrderId,
  });

  const order = http.post(`${BASE_URL}/accounts/${ACCOUNT_ID}/orders`, payload, params);
  check(order, {
    'order accepted (201/200)': (r) => r.status === 201 || r.status === 200,
  });

  sleep(1);
}

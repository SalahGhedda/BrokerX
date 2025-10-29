import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      stages: [
        { target: 50, duration: '2m' },
        { target: 150, duration: '3m' },
        { target: 0, duration: '1m' },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<400'],
    http_req_failed: ['rate<0.02'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const ACCOUNT_ID = __ENV.ACCOUNT_ID || '00000000-0000-0000-0000-000000000000';
const TOKEN = __ENV.TOKEN || '';
const headers = TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {};

export default function () {
  const stocks = http.get(`${BASE_URL}/stocks`, { headers });
  check(stocks, { 'stocks listed': (r) => r.status === 200 });

  http.get(`${BASE_URL}/accounts/${ACCOUNT_ID}/notifications`, { headers });
  http.get(`${BASE_URL}/accounts/${ACCOUNT_ID}/orders`, { headers });

  sleep(0.5);
}

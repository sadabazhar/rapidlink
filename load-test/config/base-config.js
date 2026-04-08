// Central load configuration (reusable across tests)
export const baseConfig = {

  discardResponseBodies: true,

  tags: {
      service: 'rapidlink',
      test: 'redirect-load',
  },

  stages: [
    { duration: '30s', target: 50 },    // warm-up
    { duration: '1m', target: 200 },    // normal traffic
    { duration: '2m', target: 500 },    // peak load
    { duration: '1m', target: 800 },    // stress (push limits)
    { duration: '30s', target: 0 },     // cool down
  ],

  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% requests must < 500ms
    http_req_failed: ['rate<0.25'],
    redirect_success: ['rate>0.95'],
    redirect_failure: ['rate<0.05'],
  },
};
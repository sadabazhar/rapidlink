// import json data via open(), bcz k6 doesn't support JSON import
const rawData = open('../data/shortcodes.json');
const shortcodes = JSON.parse(rawData);

import http from 'k6/http';
import { sleep, check } from 'k6';
import { Rate } from 'k6/metrics';
import { baseConfig } from '../config/base-config.js';
import { randomItem } from '../utils/helpers.js';

// k6 options
export const options = baseConfig;


// base URL (can override using env variable)
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Rate metrics for redirect correctness
const redirectSuccess = new Rate('redirect_success');
const redirectFailure = new Rate('redirect_failure');

export default function () {

  // isValid will return 80% true and 20% false
  const isValid = Math.random() < 0.8;

  // pick random shortcode (simulate 80% real traffic, 20% invalid/bot traffic)
  const shortCode = isValid
    ? randomItem(shortcodes.valid)
    : randomItem(shortcodes.invalid);

  const url = `${BASE_URL}/${shortCode}`;

  // send request
  const res = http.get(url, {
    redirects: 0,
    tags: {
      type: isValid ? 'valid' : 'invalid',
      endpoint: 'redirect',
    },
  });

  // validate response
  check(res, {
    'valid → 302': (r) => isValid && r.status === 302,
    'invalid → 404': (r) => !isValid && r.status === 404,
  });

  // Increment the counter
  if (
    (isValid && res.status === 302) ||
    (!isValid && res.status === 404)
  ) {
    redirectSuccess.add(1);
  } else {
    redirectFailure.add(1);
  }

  // simulate real user think time
  sleep(0.5 + Math.random());
}
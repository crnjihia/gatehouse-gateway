// Gatehouse Gateway Interactive Documentation & Console Logic
document.addEventListener('DOMContentLoaded', () => {
  initPipeline();
  initRateLimiter();
  initMaskingPlayground();
  initCircuitBreaker();
  initTabs();
});

/* 1. Architecture Pipeline Inspector */
const nodeData = {
  client: {
    title: "Client Request Layer",
    desc: "Ingress layer receiving external HTTP/1.1 and HTTP/2 requests with TLS termination.",
    headers: "Authorization: Bearer <RS256-JWT>\nX-Forwarded-For: 203.0.113.195\nHost: api.gatehouse.internal"
  },
  jwt: {
    title: "JwtValidationGlobalFilter (Order: -50)",
    desc: "Validates RS256 signature using JWKS public key. Inspects 'exp', 'iss', and 'aud'. Extracts 'sub', 'clientId', and 'scopes'. Mutates request headers downstream.",
    headers: "X-Client-Id: client-enterprise-99\nX-Client-Scopes: [users.read, payments.execute]\nX-Request-Id: 7e891c2b-9bf8-4687-bca9-0bcf003e83b2"
  },
  ratelimit: {
    title: "TokenBucketRateLimitFilterFactory",
    desc: "Executes an atomic Redis 7 Lua script. Enforces per-minute token bucket consumption and 24-hour daily quotas per API key or ClientId.",
    headers: "X-RateLimit-Limit: 6000\nX-RateLimit-Remaining: 5988\nX-RateLimit-Reset: 42"
  },
  transform: {
    title: "RequestResponseTransformation (Order: -2)",
    desc: "OrderedGatewayFilter decorating the Netty write pipeline. Rewrites X-Forwarded-* headers and recursively masks PCI/PII JSON fields (e.g., cardNumber, cvv, password, ssn).",
    headers: "cardNumber: ****-****-****-4444\ncvv: ******\nHost: orders.internal.svc"
  },
  circuitbreaker: {
    title: "Resilience4j Circuit Breaker",
    desc: "Sliding window size 20, 50% failure rate threshold, 15s wait in OPEN state. Routes failing requests directly to /fallback returning RFC 7807 503 ProblemDetail.",
    headers: "HTTP/1.1 503 Service Unavailable\nContent-Type: application/problem+json\nRetry-After: 15"
  },
  downstream: {
    title: "Downstream Microservices (Netty Routing)",
    desc: "Non-blocking high-throughput routing over Netty pooled connections with elastic worker pools.",
    headers: "HTTP/1.1 200 OK\nContent-Type: application/json\nX-Response-Time: 18ms"
  }
};

function initPipeline() {
  const nodes = document.querySelectorAll('.pipeline-node');
  const titleEl = document.getElementById('inspector-title');
  const descEl = document.getElementById('inspector-desc');
  const headersEl = document.getElementById('inspector-headers');

  nodes.forEach(node => {
    node.addEventListener('click', () => {
      nodes.forEach(n => n.classList.remove('active'));
      node.classList.add('active');

      const key = node.getAttribute('data-node');
      const data = nodeData[key];
      if (data && titleEl && descEl && headersEl) {
        titleEl.textContent = data.title;
        descEl.textContent = data.desc;
        headersEl.textContent = data.headers;
      }
    });
  });
}

/* 2. Token Bucket Rate Limiter Simulator */
let currentPlan = 'FREE';
const planLimits = {
  FREE: { rate: 60, quota: 1000 },
  PRO: { rate: 600, quota: 50000 },
  ENTERPRISE: { rate: 6000, quota: 1000000 }
};

let tokens = 60;
let maxTokens = 60;
let requestCount = 0;

function initRateLimiter() {
  const planBtns = document.querySelectorAll('.plan-btn');
  const sendBtn = document.getElementById('btn-send-req');
  const burstBtn = document.getElementById('btn-burst-req');
  const waterEl = document.getElementById('bucket-water');
  const labelEl = document.getElementById('bucket-label');
  const headersEl = document.getElementById('rate-headers');

  function updateVisuals() {
    const percent = Math.max(0, Math.min(100, (tokens / maxTokens) * 100));
    if (waterEl) waterEl.style.height = `${percent}%`;
    if (labelEl) labelEl.textContent = `${tokens} / ${maxTokens} Tokens`;

    if (tokens === 0 && waterEl) {
      waterEl.style.background = 'rgba(244, 63, 94, 0.6)';
    } else if (waterEl) {
      waterEl.style.background = 'linear-gradient(to top, rgba(56, 189, 248, 0.7), rgba(56, 189, 248, 0.3))';
    }

    if (headersEl) {
      if (tokens > 0) {
        headersEl.innerHTML = `HTTP/1.1 200 OK<br>X-RateLimit-Limit: ${maxTokens}<br>X-RateLimit-Remaining: ${tokens}<br>X-RateLimit-Reset: 45s`;
      } else {
        headersEl.innerHTML = `<span style="color:#f43f5e">HTTP/1.1 429 Too Many Requests</span><br>Retry-After: 45<br>X-RateLimit-Limit: ${maxTokens}<br>X-RateLimit-Remaining: 0<br>{"type":"about:blank","title":"Too Many Requests","status":429}`;
      }
    }
  }

  planBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      planBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      currentPlan = btn.getAttribute('data-plan');
      maxTokens = planLimits[currentPlan].rate;
      tokens = maxTokens;
      updateVisuals();
    });
  });

  if (sendBtn) {
    sendBtn.addEventListener('click', () => {
      if (tokens > 0) {
        tokens--;
      }
      updateVisuals();
    });
  }

  if (burstBtn) {
    burstBtn.addEventListener('click', () => {
      tokens = Math.max(0, tokens - 15);
      updateVisuals();
    });
  }

  // Refill tokens smoothly
  setInterval(() => {
    if (tokens < maxTokens) {
      const refillPerSec = Math.max(1, Math.floor(maxTokens / 60));
      tokens = Math.min(maxTokens, tokens + refillPerSec);
      updateVisuals();
    }
  }, 1000);

  updateVisuals();
}

/* 3. Sensitive Data Masking Playground */
function initMaskingPlayground() {
  const inputEl = document.getElementById('mask-input');
  const outputEl = document.getElementById('mask-output');

  if (!inputEl || !outputEl) return;

  function runMask() {
    try {
      const parsed = JSON.parse(inputEl.value);
      maskObject(parsed);
      outputEl.textContent = JSON.stringify(parsed, null, 2);
    } catch (e) {
      outputEl.textContent = "Invalid JSON input...";
    }
  }

  function maskObject(obj) {
    for (let key in obj) {
      if (typeof obj[key] === 'object' && obj[key] !== null) {
        maskObject(obj[key]);
      } else if (typeof obj[key] === 'string') {
        const lowerKey = key.toLowerCase();
        if (lowerKey.includes('card') && obj[key].length >= 4) {
          const lastFour = obj[key].slice(-4);
          obj[key] = '****-****-****-' + lastFour;
        } else if (['cvv', 'password', 'ssn', 'secret', 'apikey'].includes(lowerKey)) {
          obj[key] = '******';
        }
      }
    }
  }

  inputEl.addEventListener('input', runMask);
  runMask();
}

/* 4. Resilience4j Circuit Breaker Simulator */
function initCircuitBreaker() {
  const badgeClosed = document.getElementById('cb-closed');
  const badgeOpen = document.getElementById('cb-open');
  const badgeHalf = document.getElementById('cb-half');
  const outputEl = document.getElementById('cb-output');
  const btnTrip = document.getElementById('btn-cb-trip');
  const btnReset = document.getElementById('btn-cb-reset');

  function setState(state) {
    [badgeClosed, badgeOpen, badgeHalf].forEach(b => {
      if (b) {
        b.classList.remove('active-closed', 'active-open', 'active-half');
      }
    });

    if (state === 'CLOSED' && badgeClosed && outputEl) {
      badgeClosed.classList.add('active-closed');
      outputEl.innerHTML = `<strong>State: CLOSED</strong> (Healthy)<br>Failure rate: 0.0% &lt; 50% threshold.<br>Requests routed directly to downstream services. Latency: ~14ms.`;
    } else if (state === 'OPEN' && badgeOpen && outputEl) {
      badgeOpen.classList.add('active-open');
      outputEl.innerHTML = `<strong style="color:#f43f5e">State: OPEN</strong> (Tripped!)<br>50% failure rate exceeded in 20-call sliding window.<br>Downstream calls blocked! Fast-fallback executed:<br><pre style="margin-top:0.5rem;padding:0.5rem;">HTTP/1.1 503 Service Unavailable\n{\n  "type": "about:blank",\n  "title": "Service Unavailable",\n  "status": 503,\n  "detail": "Downstream service failed or circuit breaker is OPEN."\n}</pre>`;
    } else if (state === 'HALF_OPEN' && badgeHalf && outputEl) {
      badgeHalf.classList.add('active-half');
      outputEl.innerHTML = `<strong style="color:#f59e0b">State: HALF_OPEN</strong> (Probing)<br>Wait duration of 15s elapsed. Permitting 5 trial calls to test downstream recovery.`;
    }
  }

  if (btnTrip) {
    btnTrip.addEventListener('click', () => {
      setState('OPEN');
      setTimeout(() => setState('HALF_OPEN'), 4000);
      setTimeout(() => setState('CLOSED'), 7000);
    });
  }

  if (btnReset) {
    btnReset.addEventListener('click', () => setState('CLOSED'));
  }

  setState('CLOSED');
}

/* 5. Code Tabs */
function initTabs() {
  const tabBtns = document.querySelectorAll('.tab-btn');
  const tabContents = document.querySelectorAll('.tab-content');

  tabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      tabBtns.forEach(b => b.classList.remove('active'));
      tabContents.forEach(c => c.classList.remove('active'));

      btn.classList.add('active');
      const target = btn.getAttribute('data-tab');
      const content = document.getElementById(target);
      if (content) content.classList.add('active');
    });
  });
}

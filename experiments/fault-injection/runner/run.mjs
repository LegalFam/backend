import fs from 'node:fs'
import path from 'node:path'
import { parseArgs } from 'node:util'
import { chromium } from 'playwright'
import { startFaultProxy } from '../fault-proxy/proxy.mjs'
import {
  api, BACKEND_DIR, db, FRONTEND_DIR, FRONTEND_URL, gitCommit, gitDirty, login, MINUTE, mockHits,
  MOCK_URL, QUESTION, sleep, USER_EMAIL,
} from './lib.mjs'
import {
  backendLogFiles, killBackend, startBackend, startRabbit, stopRabbit, waitBackendHealthy,
} from './services.mjs'

const { values: args } = parseArgs({
  options: {
    scenario: { type: 'string' },
    n: { type: 'string', default: '1' },
    concurrency: { type: 'string', default: '10' },
    out: { type: 'string' },
    'proxy-base': { type: 'string', default: '9100' },
    'long-fault-min': { type: 'string', default: '12' },
    'window-min': { type: 'string', default: '30' },
    'reopen-min': { type: 'string', default: '15' },
    headed: { type: 'boolean', default: false },
    'trace-network': { type: 'boolean', default: false },
  },
})

const SCENARIOS = args.scenario.split(',')
const N = Number(args.n)
const CONCURRENCY = Number(args.concurrency)
const OUT_DIR = path.resolve(args.out)
const LONG_FAULT_MS = Number(args['long-fault-min']) * MINUTE
const WINDOW_MS = Number(args['window-min']) * MINUTE
const REOPEN_MS = Number(args['reopen-min']) * MINUTE
const MOCK_DELAY_MS = 20_000
const LEAD_MS = 5_000
const GRACE_MS = 15_000
const BATCH_SCENARIOS = new Set(['S5', 'S6'])

if (SCENARIOS.some((s) => BATCH_SCENARIOS.has(s)) && SCENARIOS.length > 1) {
  throw new Error('S5 and S6 must run alone')
}

fs.mkdirSync(OUT_DIR, { recursive: true })
const resultsFile = path.join(OUT_DIR, 'results.jsonl')

const commits = {
  frontend: `${await gitCommit(FRONTEND_DIR)}${(await gitDirty(FRONTEND_DIR)) ? '-dirty' : ''}`,
  backend: `${await gitCommit(BACKEND_DIR)}${(await gitDirty(BACKEND_DIR)) ? '-dirty' : ''}`,
}

const runLog = (entry) => {
  const line = JSON.stringify({ t: Date.now(), ...entry })
  console.log(line)
  fs.appendFileSync(path.join(OUT_DIR, 'runner.log'), `${line}\n`)
}

const waitUntil = async (timestamp) => {
  const delay = timestamp - Date.now()
  if (delay > 0) await sleep(delay)
}

const poll = async (fn, { intervalMs = 250, timeoutMs = 60_000 } = {}) => {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const value = await fn()
    if (value) return value
    await sleep(intervalMs)
  }
  return null
}

const deferred = () => {
  let resolve
  const promise = new Promise((r) => { resolve = r })
  return { promise, resolve }
}

const browser = await chromium.launch({
  headless: !args.headed,
  args: ['--proxy-bypass-list=<-loopback>'],
  proxy: { server: 'http://per-context' },
})

const proxies = []
for (let slot = 0; slot < CONCURRENCY; slot += 1) {
  proxies.push(await startFaultProxy(Number(args['proxy-base']) + slot, { log: (entry) => runLog({ proxy: entry }) }))
}

async function outboxForSession(sessionId, excludeIds = []) {
  const { rows } = await db.query(
    `SELECT e.aggregate_id, e.status, e.attempt_count, e.published_at, e.read_at, e.created_at
       FROM chat_outbox_event e
      WHERE e.chat_session_id = $1 AND NOT (e.aggregate_id = ANY($2::uuid[]))
      ORDER BY e.created_at LIMIT 1`,
    [sessionId, excludeIds]
  )
  return rows[0] || null
}

async function outboxById(assistantId) {
  const { rows } = await db.query(
    `SELECT status, attempt_count, published_at, read_at, created_at FROM chat_outbox_event WHERE aggregate_id = $1`,
    [assistantId]
  )
  return rows[0] || null
}

async function activeProcessing(userId) {
  const { rows } = await db.query(
    `SELECT 1 FROM chat_message_processing p
       JOIN chat_message m ON m.id = p.user_message_id
       JOIN chat_session s ON s.id = m.chat_session_id
      WHERE s.user_id = $1 AND p.status IN ('QUEUED', 'PROCESSING') LIMIT 1`,
    [userId]
  )
  return rows.length > 0
}

// Intenta enviar mientras la respuesta sigue sin READ: debe rebotar con assistant_receipt_pending.
async function probeLock(trial, auth) {
  const deadline = trial.t0 + WINDOW_MS + LONG_FAULT_MS + REOPEN_MS
  while (Date.now() < deadline) {
    const event = await outboxForSession(trial.session_id).catch(() => null)
    if (!event) {
      await sleep(100)
      continue
    }
    if (event.status === 'READ') return { lock_check: 'skipped_already_read' }
    const probeAt = Date.now()
    let response
    try {
      response = await api('/chat/send', {
        method: 'POST',
        token: auth.accessToken,
        body: { message: 'Consulta de control de bloqueo', sessionId: trial.session_id, language: 'es' },
      })
    } catch {
      await sleep(500)
      continue
    }
    if (response.status === 401) {
      Object.assign(auth, await login(auth.email))
      continue
    }
    if (response.status === 409 && response.data?.code === 'assistant_receipt_pending') {
      return { lock_check: 'blocked' }
    }
    if (response.status === 202) {
      const after = await outboxById(event.aggregate_id)
      const readBefore = after?.read_at && after.read_at.getTime() <= probeAt
      return {
        lock_check: readBefore ? 'accepted_after_read' : 'violation',
        probe_user_message_id: response.data?.userMessageId,
      }
    }
    return { lock_check: `unexpected_${response.status}_${response.data?.code ?? ''}` }
  }
  return { lock_check: 'no_assistant_event' }
}

function attachNetworkTrace(page, logs, pageIndex) {
  const pathOf = (request) => new URL(request.url()).pathname.replace('/api/v1', '')
  page.on('request', (request) => logs.push({ page: pageIndex, ev: 'net_request', method: request.method(), path: pathOf(request), t: Date.now() }))
  page.on('requestfinished', async (request) => {
    const response = await request.response().catch(() => null)
    logs.push({ page: pageIndex, ev: 'net_finished', method: request.method(), path: pathOf(request), status: response?.status(), t: Date.now() })
  })
  page.on('requestfailed', (request) => logs.push({ page: pageIndex, ev: 'net_failed', method: request.method(), path: pathOf(request), error: request.failure()?.errorText, t: Date.now() }))
}

function attachConsole(page, logs, pageIndex) {
  if (args['trace-network']) attachNetworkTrace(page, logs, pageIndex)
  page.on('console', (message) => {
    const text = message.text()
    if (!text.startsWith('[FAULT-INJECTION]')) return
    try {
      logs.push({ page: pageIndex, ...JSON.parse(text.slice('[FAULT-INJECTION]'.length).trim()) })
    } catch {
      // línea no parseable
    }
  })
}

async function openChat(context, sessionId, logs, pageIndex) {
  const page = await context.newPage()
  attachConsole(page, logs, pageIndex)
  await page.goto(`${FRONTEND_URL}/chat/${sessionId}`)
  return page
}

function parseBackendAttempts(assistantId) {
  const attempts = []
  const pattern = new RegExp(
    `^(\\S+)\\s.*\\[FAULT-INJECTION\\] delivery_attempt messageId=${assistantId} delivered=(true|false) status=\\S+ attemptCount=(\\d+)`
  )
  for (const file of backendLogFiles()) {
    for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
      if (!line.includes(assistantId)) continue
      const match = line.match(pattern)
      if (match) attempts.push({ t: Date.parse(match[1]), delivered: match[2] === 'true', attempt: Number(match[3]) })
    }
  }
  return attempts.sort((a, b) => a.t - b.t)
}

function derivePath(logs, assistantId, attempts) {
  const arrivals = logs.filter((log) => log.id === assistantId && (log.ev === 'sse' || log.ev === 'history'))
  if (!arrivals.length) return null
  const first = arrivals.reduce((a, b) => (a.t <= b.t ? a : b))
  if (first.ev === 'history') return 'history'
  const dispatched = attempts.filter((attempt) => attempt.delivered && attempt.t <= first.t + 1000)
  const attempt = dispatched.length ? dispatched[dispatched.length - 1].attempt : 1
  return attempt > 1 ? 'retry_sse' : 'sse'
}

async function runTrial({ trialId, scenario, slot, batch }) {
  const email = USER_EMAIL(slot + 1)
  const proxy = proxies[slot]
  const trial = {
    trial_id: trialId,
    scenario,
    commit_frontend: commits.frontend,
    commit_backend: commits.backend,
    user: email,
    proxy_port: proxy.port,
    batch_id: batch?.id ?? null,
    notes: [],
  }
  const logs = []
  let context
  try {
    const auth = { email, ...(await login(email)) }
    const userId = auth.user.id
    await poll(async () => !(await activeProcessing(userId)), { timeoutMs: 120_000 })

    const session = await api('/chat/sessions', { method: 'POST', token: auth.accessToken })
    trial.session_id = session.data.id
    await fetch(`${MOCK_URL}/delay`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId: trial.session_id, delayMs: MOCK_DELAY_MS }),
    })
    const { rows: subscriptionBefore } = await db.query('SELECT remaining_tokens FROM subscriptions WHERE user_id = $1', [userId])

    context = await browser.newContext({ proxy: { server: `http://127.0.0.1:${proxy.port}`, bypass: '' } })
    const storedAuth = { state: { accessToken: auth.accessToken, refreshToken: auth.refreshToken, user: auth.user }, version: 0 }
    await context.addInitScript((value) => {
      window.localStorage.setItem('legalfam-auth', value)
    }, JSON.stringify(storedAuth))

    let page = await openChat(context, trial.session_id, logs, 0)
    const connected = await poll(() => logs.find((log) => log.ev === 'conn' && log.state === 'connected'), { timeoutMs: 30_000 })
    if (!connected) throw new Error('SSE never connected')

    const input = page.locator('textarea').first()
    await input.fill(QUESTION)
    trial.t_send = Date.now()
    await input.press('Enter')

    const userMessage = await poll(async () => {
      const { rows } = await db.query(
        `SELECT id FROM chat_message WHERE chat_session_id = $1 AND role = 'USER' ORDER BY created_at LIMIT 1`,
        [trial.session_id]
      )
      return rows[0]
    }, { timeoutMs: 30_000 })
    if (!userMessage) throw new Error('user message was not persisted')
    trial.user_message_id = userMessage.id

    const hit = await poll(async () => (await mockHits(trial.session_id))[0], { timeoutMs: 60_000 })
    if (!hit) throw new Error('mock n8n was never called')
    trial.t0 = hit.receivedAt + hit.delayMs
    batch?.reportT0(trial)

    const probe = probeLock(trial, auth)

    if (scenario === 'S0') {
      trial.t_fault_start = null
      trial.t_fault_end = trial.t0
    } else if (scenario === 'S1a' || scenario === 'S1b' || scenario === 'S2') {
      await waitUntil(trial.t0 - LEAD_MS)
      trial.t_fault_start = Date.now()
      proxy.setMode(scenario === 'S2' ? 'blackhole' : 'reset')
      await sleep(scenario === 'S1a' ? 30_000 : LONG_FAULT_MS)
      proxy.setMode('pass')
      trial.t_fault_end = Date.now()
    } else if (scenario === 'S3') {
      await waitUntil(trial.t0 - LEAD_MS)
      const abortReceipt = (route) => route.abort('connectionreset')
      trial.t_fault_start = Date.now()
      await context.route('**/receipt', abortReceipt)
      await sleep(LONG_FAULT_MS)
      await context.unroute('**/receipt', abortReceipt)
      trial.t_fault_end = Date.now()
    } else if (scenario === 'S4') {
      await waitUntil(trial.t0 - LEAD_MS)
      trial.t_fault_start = Date.now()
      await page.close()
      page = null
      await sleep(REOPEN_MS)
      page = await openChat(context, trial.session_id, logs, 1)
      trial.t_fault_end = Date.now()
    } else if (BATCH_SCENARIOS.has(scenario)) {
      const window = await batch.faultWindow
      Object.assign(trial, window)
    }

    let assistantId = null
    let domCopiesMax = 0
    const deadline = trial.t_fault_end + WINDOW_MS
    let doneAt = null
    while (Date.now() < deadline && (!doneAt || Date.now() < doneAt + GRACE_MS)) {
      if (!assistantId) {
        const event = await outboxForSession(trial.session_id).catch(() => null)
        assistantId = event?.aggregate_id ?? null
      }
      if (assistantId) {
        if (page && !page.isClosed()) {
          const copies = await page.locator(`[data-message-id="${assistantId}"]`).count().catch(() => 0)
          domCopiesMax = Math.max(domCopiesMax, copies)
          if (copies > 0 && !trial.t_visible) trial.t_visible = Date.now()
        }
        const event = await outboxById(assistantId).catch(() => null)
        if (event?.status === 'READ' && !trial.t_read) trial.t_read = event.read_at.getTime()
        if (trial.t_visible && trial.t_read && !doneAt) doneAt = Date.now()
      }
      await sleep(500)
    }

    Object.assign(trial, await probe)
    trial.message_id = assistantId
    const excluded = trial.probe_user_message_id ? [trial.probe_user_message_id] : []
    const outbox = assistantId ? await outboxById(assistantId) : null
    trial.outbox_final = outbox
      ? { status: outbox.status, attempt_count: outbox.attempt_count, published_at: outbox.published_at?.getTime() ?? null, read_at: outbox.read_at?.getTime() ?? null, created_at: outbox.created_at.getTime() }
      : null

    const { rows: assistantRows } = await db.query(
      `SELECT count(*)::int AS rows FROM chat_message a
        WHERE a.chat_session_id = $1 AND a.role = 'ASSISTANT'
          AND a.created_at < COALESCE((SELECT created_at FROM chat_message WHERE id = ANY($2::uuid[]) LIMIT 1), 'infinity')`,
      [trial.session_id, excluded]
    )
    trial.db_assistant_rows = assistantRows[0].rows
    const { rows: consumption } = await db.query(
      `SELECT count(*)::int AS rows, COALESCE(SUM(-token_delta), 0)::int AS credits
         FROM token_transactions WHERE chat_message_id = $1 AND type = 'CHAT_CONSUMPTION'`,
      [trial.user_message_id]
    )
    trial.credits_charged = consumption[0].credits
    trial.credit_transactions = consumption[0].rows
    const { rows: subscriptionAfter } = await db.query('SELECT remaining_tokens FROM subscriptions WHERE user_id = $1', [userId])
    trial.subscription_delta = subscriptionBefore[0].remaining_tokens - subscriptionAfter[0].remaining_tokens

    const attempts = assistantId ? parseBackendAttempts(assistantId) : []
    trial.sse_events_same_id = logs.filter((log) => log.ev === 'sse' && log.id === assistantId).length
    trial.history_arrivals = logs.filter((log) => log.ev === 'history' && log.id === assistantId).length
    trial.receipts_failed = logs.filter((log) => log.ev === 'receipt' && log.id === assistantId && !log.ok).length
    trial.dom_copies = domCopiesMax
    trial.path = assistantId ? derivePath(logs, assistantId, attempts) : null
    trial.backend_attempts = attempts
    trial.conn_events = logs.filter((log) => log.ev === 'conn').map(({ page: p, state, t }) => ({ page: p, state, t }))
    if (args['trace-network']) trial.network = logs.filter((log) => log.ev.startsWith('net_'))
    trial.delivered = Boolean(trial.t_visible && trial.t_read)
    trial.lock_violation = trial.lock_check === 'violation'
  } catch (error) {
    trial.error = error.message
    trial.delivered = false
  } finally {
    proxy.setMode('pass')
    await context?.close().catch(() => {})
  }
  fs.appendFileSync(resultsFile, `${JSON.stringify(trial)}\n`)
  runLog({ trial_done: trial.trial_id, delivered: trial.delivered, path: trial.path, error: trial.error })
  return trial
}

async function runParallel() {
  const queue = []
  for (let i = 1; i <= N; i += 1) {
    for (const scenario of SCENARIOS) queue.push({ scenario, trialId: `${scenario}-${String(i).padStart(3, '0')}` })
  }
  const freeSlots = Array.from({ length: CONCURRENCY }, (_, slot) => slot)
  const running = new Set()
  while (queue.length || running.size) {
    while (queue.length && freeSlots.length) {
      const slot = freeSlots.shift()
      const job = queue.shift()
      const promise = runTrial({ ...job, slot }).finally(() => {
        running.delete(promise)
        freeSlots.push(slot)
      })
      running.add(promise)
    }
    await Promise.race(running)
  }
}

async function runBatches(scenario) {
  const batchCount = Math.ceil(N / CONCURRENCY)
  let trialNumber = 0
  for (let b = 0; b < batchCount; b += 1) {
    const size = Math.min(CONCURRENCY, N - trialNumber)
    const t0Reports = []
    const allT0 = deferred()
    const windowReady = deferred()
    const batch = {
      id: `${scenario}-batch-${b + 1}`,
      faultWindow: windowReady.promise,
      reportT0: (trial) => {
        t0Reports.push(trial)
        if (t0Reports.length === size) allT0.resolve()
      },
    }
    const trials = []
    for (let slot = 0; slot < size; slot += 1) {
      trialNumber += 1
      trials.push(runTrial({ scenario, slot, batch, trialId: `${scenario}-${String(trialNumber).padStart(3, '0')}` }))
    }
    await Promise.race([allT0.promise, sleep(120_000)])
    const t0s = t0Reports.map((trial) => trial.t0)
    const window = {}
    if (scenario === 'S5') {
      await waitUntil(Math.min(...t0s) - LEAD_MS)
      window.t_fault_start = Date.now()
      stopRabbit()
      runLog({ batch: batch.id, rabbit: 'stopped' })
      await sleep(LONG_FAULT_MS)
      startRabbit()
      window.t_fault_end = Date.now()
      runLog({ batch: batch.id, rabbit: 'started' })
    } else {
      await waitUntil(Math.max(...t0s))
      await poll(async () => {
        const outboxes = await Promise.all(t0Reports.map((trial) => outboxForSession(trial.session_id)))
        return outboxes.every(Boolean)
      }, { intervalMs: 20, timeoutMs: 10_000 })
      const statuses = await Promise.all(t0Reports.map((trial) => outboxForSession(trial.session_id)))
      window.t_fault_start = Date.now()
      const pid = killBackend()
      runLog({ batch: batch.id, killed: pid, outbox_at_kill: statuses.map((s) => s?.status ?? null) })
      startBackend(`backend-${batch.id}-restart.log`)
      window.t_fault_end = await waitBackendHealthy()
      runLog({ batch: batch.id, backend: 'healthy' })
    }
    windowReady.resolve(window)
    await Promise.all(trials)
  }
}

runLog({ run: 'start', scenarios: SCENARIOS, n: N, concurrency: CONCURRENCY, commits })
try {
  if (BATCH_SCENARIOS.has(SCENARIOS[0])) await runBatches(SCENARIOS[0])
  else await runParallel()
} finally {
  for (const proxy of proxies) proxy.close()
  await browser.close()
  await db.end()
  runLog({ run: 'end' })
}

/*
 * Page-context structured job bridge for the Tampermonkey userscript.
 *
 * The main userscript injects an equivalent function at runtime because a
 * userscript cannot reliably load a sibling local file as a page script.
 * Keeping this standalone copy in the repository makes the page-context
 * boundary explicit and easy to inspect. It intentionally reads only a
 * small whitelist of job fields and performs no network or credential work.
 */
(function () {
  'use strict';

  const CHANNEL = 'AI_JOB_SCREENING_VUE_BRIDGE_V1';
  const MAX_DEPTH = 6;
  const MAX_NODES = 3000;
  const MAX_ATTEMPTS = 24;
  const SKIP_KEYS = new Set([
    'parent', 'parentNode', 'ownerDocument', 'el', 'elm', 'componentInstance',
    'subTree', 'vnode', 'children', 'childNodes', 'listeners', 'events',
    'render', 'staticRenderFns', 'proxyMap', 'cache', 'computed', 'watchers'
  ]);
  const NOISE_TEXT = /(?:\u6536\u85cf|\u4e3e\u62a5|\u5fae\u4fe1|\u626b\u7801|\u5206\u4eab|\u7acb\u5373\u6c9f\u901a|\u7acb\u5373\u6295\u9012|BOSS|HR)/i;

  const text = value => String(value == null ? '' : value)
    .replace(/\u0000/g, '')
    .replace(/\s+/g, ' ')
    .trim();

  const isObject = value => value !== null && (typeof value === 'object' || typeof value === 'function');

  function safeGet(object, key) {
    try {
      return object && object[key];
    } catch (error) {
      return undefined;
    }
  }

  function unwrapRef(value) {
    let current = value;
    for (let index = 0; index < 3; index += 1) {
      if (!isObject(current) || !Object.prototype.hasOwnProperty.call(current, 'value')) break;
      const next = safeGet(current, 'value');
      if (next === current || next == null) break;
      current = next;
    }
    return current;
  }

  function asText(value) {
    if (typeof value === 'string' || typeof value === 'number') return text(value);
    if (!value || typeof value !== 'object') return '';
    return text(value.name || value.label || value.text || value.value || value.desc || value.description);
  }

  function asTextArray(value) {
    const values = Array.isArray(value) ? value : (value == null ? [] : [value]);
    const seen = new Set();
    const result = [];
    for (const item of values) {
      const valueText = asText(item);
      if (!valueText) continue;
      if (valueText.length > 48 || NOISE_TEXT.test(valueText)) continue;
      const key = valueText.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      result.push(valueText);
    }
    return result.slice(0, 60);
  }

  function ownValue(object, keys) {
    for (const key of keys) {
      const value = safeGet(object, key);
      if (Array.isArray(value) && value.length) return value;
      if (asText(value)) return value;
    }
    return '';
  }

  function nestedValue(object, keys) {
    const source = unwrapRef(object);
    const direct = ownValue(source, keys);
    if (direct) return direct;
    const nestedKeys = ['jobInfo', 'job', 'position', 'detail', 'data', 'info', 'brand', 'company'];
    for (const nestedKey of nestedKeys) {
      const nested = unwrapRef(safeGet(source, nestedKey));
      if (!isObject(nested)) continue;
      const value = ownValue(nested, keys);
      if (value) return value;
    }
    return '';
  }

  function jobScore(object) {
    if (!isObject(object)) return 0;
    const keys = [
      'jobName', 'positionName', 'salaryDesc', 'salary', 'brandName', 'companyName',
      'postDescription', 'jobDescription', 'skills', 'jobLabels', 'jobDegree',
      'degreeName', 'jobExperience', 'experienceName', 'locationName', 'cityName'
    ];
    let score = 0;
    for (const key of keys) {
      if (safeGet(object, key) != null) score += 1;
    }
    const raw = nestedValue(object, ['postDescription', 'jobDescription', 'description']);
    if (asText(raw).length >= 80) score += 8;
    if (nestedValue(object, ['jobName', 'positionName'])) score += 3;
    return score;
  }

  function addAttempt(attempts, value) {
    const item = text(value);
    if (item && attempts.length < MAX_ATTEMPTS && !attempts.includes(item)) attempts.push(item);
  }

  function addCandidate(add, value, label) {
    const unwrapped = unwrapRef(value);
    if (Array.isArray(unwrapped)) {
      add(unwrapped, label + '[]');
      for (const [index, item] of unwrapped.entries()) {
        if (index >= 80) break;
        add(unwrapRef(item), label + '[' + index + ']');
      }
      return;
    }
    add(unwrapped, label);
  }

  function addVueDataCandidates(add, vue, label) {
    const sources = [
      ['root', vue],
      ['proxy', safeGet(vue, 'proxy')],
      ['ctx', safeGet(vue, 'ctx')],
      ['setupState', safeGet(vue, 'setupState')],
      ['data', safeGet(vue, 'data')],
      ['$data', safeGet(vue, '$data')],
      ['_data', safeGet(vue, '_data')],
      ['props', safeGet(vue, 'props')]
    ];
    const keys = [
      'jobDetail', '_jobDetail', 'jobList', '_jobList', 'jobData',
      '_jobDataMap', 'currentJob', 'jobInfo', 'detail', 'value'
    ];
    for (const [sourceName, source] of sources) {
      const unwrapped = unwrapRef(source);
      if (!isObject(unwrapped)) continue;
      for (const key of keys) {
        const value = safeGet(unwrapped, key);
        if (value != null) addCandidate(add, value, label + '.' + sourceName + '.' + key);
      }
    }
  }

  function collectRoots(attempts) {
    const roots = [];
    const seen = new Set();
    const selectors = [
      '[data-v-app]', '#wrap', '.job-detail', '.job-detail-box', '.job-primary',
      '[class*="job-detail"]', '[class*="jobDetail"]', '[class*="job-primary"]',
      'main', 'body'
    ];
    const add = (value, label) => {
      if (!value || !isObject(value) || seen.has(value)) return;
      seen.add(value);
      roots.push(value);
      addAttempt(attempts, label);
    };
    for (const selector of selectors) {
      let nodes = [];
      try {
        nodes = Array.from(document.querySelectorAll(selector));
      } catch (error) {
        addAttempt(attempts, 'selector error: ' + selector);
      }
      for (const node of nodes.slice(0, 40)) {
        const vue2 = safeGet(node, '__vue__');
        const vue3 = safeGet(node, '__vueParentComponent');
        if (vue2) {
          add(vue2, selector + '.__vue__');
          addVueDataCandidates(add, vue2, selector + '.__vue__');
        }
        if (vue3) {
          add(vue3, selector + '.__vueParentComponent');
          add(safeGet(vue3, 'proxy'), selector + '.proxy');
          add(safeGet(vue3, 'ctx'), selector + '.ctx');
          add(safeGet(vue3, 'setupState'), selector + '.setupState');
          add(safeGet(vue3, 'data'), selector + '.data');
          add(safeGet(vue3, 'props'), selector + '.props');
          addVueDataCandidates(add, vue3, selector + '.__vueParentComponent');
        }
      }
    }
    add(safeGet(document, 'body'), 'document.body');
    return roots;
  }

  function findJobObjects(root, attempts) {
    const found = [];
    const visited = new Set();
    let inspected = 0;
    function walk(value, depth) {
      if (!isObject(value) || depth > MAX_DEPTH || inspected >= MAX_NODES || visited.has(value)) return;
      visited.add(value);
      inspected += 1;
      const score = jobScore(value);
      if (score >= 5) found.push({ object: value, score });
      let keys = [];
      try {
        keys = Object.keys(value).slice(0, 120);
      } catch (error) {
        return;
      }
      for (const key of keys) {
        const allowedPrivateKey = key === '_jobDetail' || key === '_jobList' || key === '_jobDataMap' || key === '_data';
        if (SKIP_KEYS.has(key) || (key.startsWith('_') && !allowedPrivateKey)) continue;
        const child = safeGet(value, key);
        if (isObject(child)) walk(child, depth + 1);
      }
    }
    walk(root, 0);
    addAttempt(attempts, 'inspected Vue objects: ' + inspected);
    return found;
  }

  function buildSnapshot(object) {
    const jobTitle = asText(nestedValue(object, ['jobName', 'positionName', 'title', 'name']));
    const companyName = asText(nestedValue(object, ['brandName', 'companyName', 'company']));
    const salary = asText(nestedValue(object, ['salaryDesc', 'salary', 'salaryText']));
    const city = asText(nestedValue(object, ['locationName', 'cityName', 'city', 'cityDesc']));
    const education = asText(nestedValue(object, ['jobDegree', 'degreeName', 'education', 'degree']));
    const experience = asText(nestedValue(object, ['jobExperience', 'experienceName', 'experience']));
    const skills = asTextArray(nestedValue(object, ['skills', 'skillList', 'skillLabels']));
    const jobTags = asTextArray(nestedValue(object, ['jobLabels', 'labels', 'tags']));
    const rawJD = asText(nestedValue(object, ['postDescription', 'jobDescription', 'description', 'rawJD']));
    return { jobTitle, companyName, salary, city, education, experience, skills, jobTags, rawJD };
  }

  function readSnapshot() {
    const attempts = [];
    const roots = collectRoots(attempts);
    const candidates = [];
    for (const root of roots) candidates.push(...findJobObjects(root, attempts));
    candidates.sort((left, right) => {
      const leftRaw = asText(nestedValue(left.object, ['postDescription', 'jobDescription', 'description'])).length;
      const rightRaw = asText(nestedValue(right.object, ['postDescription', 'jobDescription', 'description'])).length;
      return (right.score + Math.min(rightRaw / 100, 10)) - (left.score + Math.min(leftRaw / 100, 10));
    });
    const snapshot = candidates.length ? buildSnapshot(candidates[0].object) : null;
    const found = Boolean(snapshot && (snapshot.jobTitle || snapshot.companyName || snapshot.rawJD));
    if (!roots.length) addAttempt(attempts, 'no Vue marker found on candidate DOM nodes');
    if (!candidates.length) addAttempt(attempts, 'no job-shaped Vue object found');
    if (found) addAttempt(attempts, 'selected best job-shaped object');
    return {
      found,
      snapshot,
      attempts: attempts.slice(0, MAX_ATTEMPTS),
      reason: found ? '' : 'Vue marker or job-shaped data was not found'
    };
  }

  window.addEventListener('message', event => {
    if (event.source !== window || !event.data || event.data.channel !== CHANNEL) return;
    if (event.data.type !== 'request' || event.data.action !== 'readJobSnapshot') return;
    let result;
    try {
      result = readSnapshot();
    } catch (error) {
      result = { found: false, snapshot: null, attempts: ['bridge error: ' + text(error && error.message)], reason: 'bridge error' };
    }
    window.postMessage({
      channel: CHANNEL,
      type: 'response',
      action: 'readJobSnapshot',
      requestId: event.data.requestId,
      ...result
    }, '*');
  });
})();

/*
 * Reusable page-context reader for BOSS Vue state.
 *
 * It mirrors the reader used by the Tampermonkey bridge and intentionally
 * returns only ordinary, whitelisted job fields. It does not perform network
 * requests or read cookies/tokens. The running userscript injects its bridge
 * inline; this file is kept as the independently reviewable extractor module.
 */
(function (global) {
  'use strict';

  const MAX_DEPTH = 6;
  const MAX_NODES = 3000;
  const NOISE_TEXT = /(?:收藏|举报|微信|扫码|分享|立即沟通|立即投递|BOSS|HR|登录|注册|首页|推荐|附近|筛选)/i;
  const SKIP_KEYS = new Set([
    'parent', 'parentNode', 'ownerDocument', 'el', 'elm', 'componentInstance',
    'subTree', 'vnode', 'children', 'childNodes', 'listeners', 'events',
    'render', 'staticRenderFns', 'proxyMap', 'cache', 'computed', 'watchers'
  ]);

  const clean = value => String(value == null ? '' : value)
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
    if (typeof value === 'string' || typeof value === 'number') return clean(value);
    if (!value || typeof value !== 'object') return '';
    return clean(value.name || value.label || value.text || value.value || value.desc || value.description);
  }

  function asTextArray(value) {
    const values = Array.isArray(value) ? value : (value == null ? [] : [value]);
    const seen = new Set();
    const result = [];
    for (const item of values) {
      const itemText = asText(item);
      if (!itemText || itemText.length > 48 || NOISE_TEXT.test(itemText)) continue;
      const key = itemText.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      result.push(itemText);
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
    for (const nestedKey of ['jobInfo', 'job', 'position', 'detail', 'data', 'info', 'brand', 'company']) {
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
    let score = keys.reduce((total, key) => total + (safeGet(object, key) != null ? 1 : 0), 0);
    if (asText(nestedValue(object, ['postDescription', 'jobDescription', 'description'])).length >= 80) score += 8;
    if (nestedValue(object, ['jobName', 'positionName'])) score += 3;
    return score;
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
      ['root', vue], ['proxy', safeGet(vue, 'proxy')], ['ctx', safeGet(vue, 'ctx')],
      ['setupState', safeGet(vue, 'setupState')], ['data', safeGet(vue, 'data')],
      ['$data', safeGet(vue, '$data')], ['_data', safeGet(vue, '_data')],
      ['props', safeGet(vue, 'props')]
    ];
    const keys = ['jobDetail', '_jobDetail', 'jobList', '_jobList', 'jobData', '_jobDataMap', 'currentJob', 'jobInfo', 'detail', 'value'];
    for (const [sourceName, source] of sources) {
      const unwrapped = unwrapRef(source);
      if (!isObject(unwrapped)) continue;
      for (const key of keys) {
        const value = safeGet(unwrapped, key);
        if (value != null) addCandidate(add, value, label + '.' + sourceName + '.' + key);
      }
    }
  }

  function collectRoots() {
    const roots = [];
    const seen = new Set();
    const selectors = [
      '[data-v-app]', '#wrap', '.job-detail', '.job-detail-box', '.job-primary',
      '[class*="job-detail"]', '[class*="jobDetail"]', '[class*="job-primary"]', 'main', 'body'
    ];
    const add = value => {
      if (!value || !isObject(value) || seen.has(value)) return;
      seen.add(value);
      roots.push(value);
    };
    for (const selector of selectors) {
      let nodes = [];
      try { nodes = Array.from(document.querySelectorAll(selector)); } catch (error) { nodes = []; }
      for (const node of nodes.slice(0, 40)) {
        const vue2 = safeGet(node, '__vue__');
        const vue3 = safeGet(node, '__vueParentComponent');
        if (vue2) { add(vue2); addVueDataCandidates(add, vue2, selector + '.__vue__'); }
        if (vue3) {
          add(vue3);
          add(safeGet(vue3, 'proxy')); add(safeGet(vue3, 'ctx')); add(safeGet(vue3, 'setupState'));
          add(safeGet(vue3, 'data')); add(safeGet(vue3, 'props'));
          addVueDataCandidates(add, vue3, selector + '.__vueParentComponent');
        }
      }
    }
    return { roots, add };
  }

  function readVueJobSnapshot() {
    const { roots } = collectRoots();
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
      try { keys = Object.keys(value).slice(0, 120); } catch (error) { return; }
      for (const key of keys) {
        const allowedPrivateKey = key === '_jobDetail' || key === '_jobList' || key === '_jobDataMap' || key === '_data';
        if (SKIP_KEYS.has(key) || (key.startsWith('_') && !allowedPrivateKey)) continue;
        const child = safeGet(value, key);
        if (isObject(child)) walk(child, depth + 1);
      }
    }
    roots.forEach(root => walk(root, 0));
    found.sort((left, right) => {
      const leftRaw = asText(nestedValue(left.object, ['postDescription', 'jobDescription', 'description'])).length;
      const rightRaw = asText(nestedValue(right.object, ['postDescription', 'jobDescription', 'description'])).length;
      return (right.score + Math.min(rightRaw / 100, 10)) - (left.score + Math.min(leftRaw / 100, 10));
    });
    const object = found.length ? found[0].object : null;
    const snapshot = object ? {
      jobTitle: asText(nestedValue(object, ['jobName', 'positionName', 'title', 'name'])),
      companyName: asText(nestedValue(object, ['brandName', 'companyName', 'company'])),
      salary: asText(nestedValue(object, ['salaryDesc', 'salary', 'salaryText'])),
      city: asText(nestedValue(object, ['locationName', 'cityName', 'city', 'cityDesc'])),
      education: asText(nestedValue(object, ['jobDegree', 'degreeName', 'education', 'degree'])),
      experience: asText(nestedValue(object, ['jobExperience', 'experienceName', 'experience'])),
      skills: asTextArray(nestedValue(object, ['skills', 'skillList', 'skillLabels'])),
      jobTags: asTextArray(nestedValue(object, ['jobLabels', 'labels', 'tags'])),
      rawJD: asText(nestedValue(object, ['postDescription', 'jobDescription', 'description', 'rawJD']))
    } : null;
    return { found: Boolean(snapshot && (snapshot.jobTitle || snapshot.companyName || snapshot.rawJD)), snapshot, inspected };
  }

  global.BossVueReader = { readVueJobSnapshot };
})(typeof window !== 'undefined' ? window : globalThis);

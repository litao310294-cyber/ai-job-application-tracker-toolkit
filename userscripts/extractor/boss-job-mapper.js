/*
 * Pure mapping helpers for the BOSS job capture layer.
 *
 * The Tampermonkey entrypoint keeps a small inline copy of this mapper because
 * local sibling files are not reliably importable at runtime. This file is the
 * reviewable source module and can also be loaded by a future build step.
 */
(function (global) {
  'use strict';

  const NOISE_TEXT = /(?:收藏|举报|微信|扫码|分享|立即沟通|立即投递|BOSS|HR|登录|注册|首页|推荐|附近|筛选)/i;

  function clean(value) {
    return String(value == null ? '' : value)
      .replace(/\u0000/g, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function asText(value) {
    if (typeof value === 'string' || typeof value === 'number') return clean(value);
    if (!value || typeof value !== 'object') return '';
    return clean(value.name || value.label || value.text || value.value || value.desc || value.description);
  }

  function cleanList(value) {
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

  function pick(vueValue, domValue) {
    const vueText = clean(vueValue);
    return vueText || clean(domValue);
  }

  function pickList(vueValue, domValue) {
    const vueItems = cleanList(vueValue);
    return vueItems.length ? vueItems : cleanList(domValue);
  }

  function mapSnapshotToStructuredJobInfo(vueSnapshot, domInfo) {
    const vue = vueSnapshot || {};
    const dom = domInfo || {};
    const vueFields = [
      'jobTitle', 'companyName', 'salary', 'city', 'education',
      'experience', 'skills', 'jobTags', 'rawJD'
    ];
    const result = {
      jobTitle: pick(vue.jobTitle, dom.jobTitle),
      companyName: pick(vue.companyName, dom.companyName),
      salary: pick(vue.salary, dom.salary),
      city: pick(vue.city, dom.city),
      education: pick(vue.education, dom.education),
      experience: pick(vue.experience, dom.experience),
      skills: pickList(vue.skills, dom.skills || dom.tags),
      jobTags: pickList(vue.jobTags, dom.jobTags || dom.tags),
      rawJD: pick(vue.rawJD, dom.rawJD || dom.jdText || dom.jobText)
    };
    const vueCount = vueFields.reduce((count, field) => {
      const value = Array.isArray(vue[field]) ? cleanList(vue[field]) : clean(vue[field]);
      return count + (value && (Array.isArray(value) ? value.length : true) ? 1 : 0);
    }, 0);
    const domCount = vueFields.reduce((count, field) => {
      const source = field === 'rawJD' ? (dom.rawJD || dom.jdText || dom.jobText) : (field === 'skills' ? (dom.skills || dom.tags) : (field === 'jobTags' ? (dom.jobTags || dom.tags) : dom[field]));
      const value = Array.isArray(source) ? cleanList(source) : clean(source);
      return count + (value && (Array.isArray(value) ? value.length : true) ? 1 : 0);
    }, 0);
    result.extractionMode = vueCount && domCount ? 'MIXED' : (vueCount ? 'VUE' : 'DOM');
    return result;
  }

  global.BossJobMapper = {
    cleanList,
    mapSnapshotToStructuredJobInfo
  };
})(typeof window !== 'undefined' ? window : globalThis);

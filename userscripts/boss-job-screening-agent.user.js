// ==UserScript==
// @name         BOSS Job Screening Agent
// @namespace    ai-job-screening-agent
// @version      2.2.0
// @description  Local-first AI job screening panel for visible BOSS job pages.
// @match        https://www.zhipin.com/*
// @run-at       document-end
// @grant        GM_setClipboard
// @grant        GM_xmlhttpRequest
// @connect      localhost
// @connect      127.0.0.1
// ==/UserScript==

(function () {
  'use strict';

  const BACKEND_BASE_URL = 'http://localhost:8080';
  const PANEL_ID = 'job-fit-scoring-panel';
  const STORAGE_COLLAPSED_KEY = 'aiJobScreening.panelCollapsed';

  const DEFAULT_SCORING_CONFIG = {
    targetRoles: ['Java后端', 'Java开发', '后端开发', '服务端开发', '后端研发', 'Spring Boot', 'AI应用开发', '大模型应用', 'RAG', 'Agent', 'LLM'],
    positiveKeywords: ['Java', 'Spring Boot', 'Spring Cloud', 'MySQL', 'Redis', 'MyBatis', 'Linux', 'Docker', 'RESTful', '接口开发', '缓存', '数据库设计', 'RAG', 'Agent', 'LLM', 'Tool Calling', 'Prompt'],
    negativeKeywords: ['销售', '客服', '运营', '剪辑', '标注', '外包', '驻场', '培训', '转化', '拉新', '电销', '邀约', '售前', '运维'],
    hardRejectKeywords: ['电话销售', '纯销售', '无薪', '培训收费'],
    preferredCities: ['北京', '天津', '远程']
  };

  let activeScoringConfig = DEFAULT_SCORING_CONFIG;
  let scoringConfigLoaded = false;
  let scoringConfigSource = 'default';
  let scoringConfigStatusText = '默认兜底配置';
  let lastResultKey = '';
  let lastScoreResult = null;
  let jobFitAiLoading = false;
  let jobFitAiResult = null;
  let jobFitAiError = '';
  let jobFitHistoryRecords = [];
  let jobFitHistoryLoading = false;
  let jobFitHistoryError = '';
  let jobFitHistoryKey = '';
  let jobFitFeedbackDraft = defaultFeedbackDraft();
  let jobFitFeedbackSaving = false;
  let jobFitFeedbackSaved = false;
  let jobFitFeedbackError = '';
  let jobFitCollapsed = localStorage.getItem(STORAGE_COLLAPSED_KEY) === 'true';
  let updateTimer = null;

  const clean = (value) => String(value || '')
    .replace(/\uFFFD/g, '')
    .replace(/[□�]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  function isChatPage() {
    return /\/web\/geek\/chat|\/chat/.test(location.pathname);
  }

  if (isChatPage()) {
    return;
  }

  function uniqueCaseInsensitive(items) {
    const seen = new Set();
    const result = [];
    for (const item of items || []) {
      const value = clean(item);
      if (!value) continue;
      const key = value.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      result.push(value);
    }
    return result;
  }

  function normalizeConfigArray(value) {
    if (Array.isArray(value)) return uniqueCaseInsensitive(value);
    if (typeof value === 'string') return uniqueCaseInsensitive(value.split(/[,，;；\n、/]+/));
    return [];
  }

  function mergeUniqueArrays(base, extra) {
    return uniqueCaseInsensitive([].concat(base || [], extra || []));
  }

  function mergeScoringConfig(base, remote) {
    return {
      targetRoles: mergeUniqueArrays(base.targetRoles, remote.targetRoles),
      positiveKeywords: mergeUniqueArrays(base.positiveKeywords, remote.positiveKeywords),
      negativeKeywords: mergeUniqueArrays(base.negativeKeywords, remote.negativeKeywords),
      hardRejectKeywords: mergeUniqueArrays(base.hardRejectKeywords, remote.hardRejectKeywords),
      preferredCities: mergeUniqueArrays(base.preferredCities, remote.preferredCities)
    };
  }

  function parseRemoteScoringConfig(response) {
    if (!response || !response.exists) return null;
    const raw = response.configJson;
    const config = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (!config || typeof config !== 'object') return null;
    return {
      targetRoles: normalizeConfigArray(config.targetRoles || config.target_roles),
      positiveKeywords: normalizeConfigArray(config.positiveKeywords || config.positive_keywords),
      negativeKeywords: normalizeConfigArray(config.negativeKeywords || config.negative_keywords),
      hardRejectKeywords: normalizeConfigArray(config.hardRejectKeywords || config.hard_reject_keywords),
      preferredCities: normalizeConfigArray(config.preferredCities || config.preferred_cities)
    };
  }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function compactText(value, maxLength) {
    const text = clean(value);
    return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text;
  }

  function requestJson(options) {
    return new Promise((resolve, reject) => {
      if (typeof GM_xmlhttpRequest !== 'function') {
        reject(new Error('GM_xmlhttpRequest unavailable'));
        return;
      }
      GM_xmlhttpRequest({
        method: options.method || 'GET',
        url: options.url,
        headers: Object.assign({ 'Content-Type': 'application/json' }, options.headers || {}),
        data: options.body ? JSON.stringify(options.body) : undefined,
        timeout: options.timeout || 30000,
        onload(response) {
          if (response.status < 200 || response.status >= 300) {
            reject(new Error(`HTTP ${response.status}`));
            return;
          }
          try {
            resolve(response.responseText ? JSON.parse(response.responseText) : null);
          } catch (e) {
            reject(new Error('Invalid JSON response'));
          }
        },
        onerror: () => reject(new Error('Request failed')),
        ontimeout: () => reject(new Error('Request timeout'))
      });
    });
  }

  async function loadScoringConfigFromBackend() {
    try {
      const response = await requestJson({
        url: `${BACKEND_BASE_URL}/api/profile/scoring-config`,
        timeout: 6000
      });
      const remoteConfig = parseRemoteScoringConfig(response);
      if (remoteConfig && response.confirmed) {
        activeScoringConfig = mergeScoringConfig(DEFAULT_SCORING_CONFIG, remoteConfig);
        scoringConfigLoaded = true;
        scoringConfigSource = 'backend';
        scoringConfigStatusText = '后端用户画像配置';
      } else if (remoteConfig) {
        activeScoringConfig = mergeScoringConfig(DEFAULT_SCORING_CONFIG, remoteConfig);
        scoringConfigLoaded = true;
        scoringConfigSource = 'backend-unconfirmed';
        scoringConfigStatusText = '后端用户画像配置（未确认）';
      }
    } catch (e) {
      activeScoringConfig = DEFAULT_SCORING_CONFIG;
      scoringConfigLoaded = false;
      scoringConfigSource = 'default';
      scoringConfigStatusText = '默认兜底配置';
    }
  }

  function isVisibleElement(el) {
    if (!el) return false;
    const style = window.getComputedStyle(el);
    if (style.display === 'none' || style.visibility === 'hidden') return false;
    const rect = el.getBoundingClientRect();
    return rect.width > 1 && rect.height > 1 && rect.bottom >= 0 && rect.top <= window.innerHeight + 80;
  }

  function getElementOwnText(el) {
    if (!el) return '';
    return clean(Array.from(el.childNodes).filter(node => node.nodeType === Node.TEXT_NODE).map(node => node.nodeValue).join(' '));
  }

  function getElementText(el) {
    return isVisibleElement(el) ? clean(el.innerText || el.textContent || '') : '';
  }

  function getVisibleJobText() {
    const container = findJobDetailContainer();
    return container ? getElementText(container) : clean(document.body ? document.body.innerText : '');
  }

  function findJobDetailContainer() {
    const selectors = ['.job-detail', '.job-detail-box', '.job-sec', '.job-primary', '.job-detail-container', '.job-content', '[class*="job-detail"]', '[class*="jobDetail"]'];
    for (const selector of selectors) {
      const found = Array.from(document.querySelectorAll(selector)).find(el => {
        const text = getElementText(el);
        return text.length > 120 && /职位|岗位|职责|要求|任职|Java|Spring|Redis|Rust|交易系统|服务端/.test(text);
      });
      if (found) return found;
    }
    const candidates = Array.from(document.querySelectorAll('main, section, article, div'))
      .map(el => ({ el, rect: el.getBoundingClientRect(), text: getElementText(el) }))
      .filter(item => item.text.length > 160 && item.rect.width > 300 && item.rect.height > 180 && /职位|岗位|职责|要求|任职|经验|学历|Java|Spring|Redis|Rust|服务端/.test(item.text))
      .sort((a, b) => b.text.length - a.text.length);
    return candidates.length ? candidates[0].el : null;
  }

  function findJobHeaderBlock() {
    const selectors = ['.job-banner', '.job-primary', '.job-title', '.info-primary', '[class*="job-banner"]'];
    for (const selector of selectors) {
      const found = Array.from(document.querySelectorAll(selector)).find(isVisibleElement);
      if (found) return found;
    }
    return findJobDetailContainer() || document.body;
  }

  function isSearchConditionText(text) {
    return /筛选|搜索|推荐|附近|经验不限|学历不限|职位类型|薪资待遇|公司规模|融资阶段/.test(clean(text));
  }

  function cleanJobTitle(value) {
    let title = clean(value)
      .replace(/[□�\uFFFD]+/g, ' ')
      .replace(/[^\u4e00-\u9fa5a-zA-Z0-9+.#/()（）\-_\s]/g, ' ')
      .replace(/\d+\s*[-~]\s*\d+\s*(?:元\/天|\/天|K|k|千|万|薪)?/g, ' ')
      .replace(/\d+\s*[Kk]\s*[-~]\s*\d+\s*[Kk]/g, ' ')
      .replace(/\s*[-~－—–]?\s*(?:元\/天|\/天|元\/日|\/日|[Kk])\s*$/g, ' ')
      .replace(/\s*[-~－—–]\s*(?:元\/天|\/天|元\/日|\/日|[Kk])(?=\s|$)/g, ' ')
      .replace(/面议|薪资|待遇|急聘|直招|校招|社招|经验不限|学历不限/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    if (title.length > 60) {
      title = title.split(/[，,。；;|｜]/).map(clean).find(part => part.length >= 2 && part.length <= 40) || title.slice(0, 60);
    }
    return title;
  }

  function findJobTitleElement() {
    const header = findJobHeaderBlock();
    const candidates = Array.from((header || document).querySelectorAll('h1, h2, .name, .job-title, [class*="title"]'))
      .filter(isVisibleElement)
      .map(el => ({ el, text: cleanJobTitle(getElementOwnText(el) || getElementText(el)) }))
      .filter(item => item.text && item.text.length >= 2 && item.text.length <= 60 && !isSearchConditionText(item.text));
    return candidates.length ? candidates[0].el : null;
  }

  function extractJobTitleFromHeader(header) {
    const titleEl = findJobTitleElement();
    if (titleEl) return cleanJobTitle(getElementOwnText(titleEl) || getElementText(titleEl));
    const text = getElementText(header || findJobHeaderBlock());
    const firstLine = text.split(/\n|职位描述|岗位职责|任职要求/).map(cleanJobTitle).find(Boolean);
    if (firstLine) return firstLine;
    return cleanJobTitle(document.title.split(/[-_|]/)[0]);
  }

  function parseSalary(text) {
    const value = clean(text);
    if (/面议/.test(value)) return '面议';
    const match = value.match(/(\d+\s*[-~－—–]\s*\d+\s*元\s*\/\s*天|\d+\s*[-~－—–]\s*\d+\s*\/\s*天|\d+\s*[Kk]\s*[-~－—–]\s*\d+\s*[Kk]?|\d+\s*[-~－—–]\s*\d+\s*[Kk])/);
    return match ? clean(match[1]).replace(/\s+/g, '').replace(/k/g, 'K') : '';
  }

  function extractSalaryFromHeader(header) {
    const headerSalary = parseSalary(getElementText(header || findJobHeaderBlock()));
    if (headerSalary) return headerSalary;
    const candidates = collectVisibleSalaryCandidates();
    return candidates.length ? candidates[0].salary : '';
  }

  function isLikelySearchOrListElement(el) {
    if (!el || !el.closest) return false;
    const blocker = el.closest('[class*="filter"],[class*="search"],[class*="recommend"],[class*="job-list"],[class*="list"],[class*="condition"],[class*="sidebar"],[id*="filter"],[id*="search"],[id*="list"]');
    return Boolean(blocker);
  }

  function collectVisibleSalaryCandidates() {
    const elements = Array.from(document.querySelectorAll('span, div, p, li, b, em, strong'));
    return elements
      .map(el => ({ el, text: getElementText(el), rect: el.getBoundingClientRect ? el.getBoundingClientRect() : null }))
      .filter(item => item.text && item.text.length <= 40 && item.rect && item.rect.width > 1 && item.rect.height > 1)
      .filter(item => !isLikelySearchOrListElement(item.el))
      .map(item => ({ ...item, salary: parseSalary(item.text) }))
      .filter(item => item.salary)
      .sort((a, b) => {
        const aTop = a.rect.top < 260 ? 0 : 1;
        const bTop = b.rect.top < 260 ? 0 : 1;
        if (aTop !== bTop) return aTop - bTop;
        if (Math.abs(a.rect.top - b.rect.top) > 8) return a.rect.top - b.rect.top;
        return b.rect.left - a.rect.left;
      });
  }

  function isLocationLike(value) {
    const text = clean(value);
    if (!text) return false;
    if (/工作地址|地址|附近|地铁|街道|商圈|园区|大厦|楼|号/.test(text)) return true;
    if (/^(北京|上海|深圳|广州|杭州|天津|南京|苏州|成都|武汉|西安|重庆|厦门|合肥|长沙|远程)(·|\.|-|｜|\/)/.test(text)) return true;
    if (/(北京|上海|深圳|广州|杭州|天津|南京|苏州|成都|武汉|西安|重庆|厦门|合肥|长沙)·[^·\s]{1,12}区(·[^·\s]{1,16})?/.test(text)) return true;
    if (/^(北京|上海|深圳|广州|杭州|天津|南京|苏州|成都|武汉|西安|重庆|厦门|合肥|长沙|朝阳区|浦东|南山|酒仙桥|望京|海淀|西二旗|中关村|天河|余杭|滨江)$/.test(text)) return true;
    return false;
  }

  function isValidCompanyName(value) {
    const text = clean(value);
    if (!text || text.length < 2 || text.length > 40) return false;
    if (isLocationLike(text)) return false;
    if (/职位|岗位|招聘|在招|薪资|经验|学历|实习|详情/.test(text)) return false;
    if (/^(件及网络|文件及网络|网络I\/O|网络IO|消息队列|技术栈|工作职责|任职资格|岗位职责|职位描述|高可用|稳定性|服务端|服务端开发|后端开发|分布式消息队列|消息队列产品|工程项目|比赛中担任队长)$/.test(text)) return false;
    if (/文件及网络|网络\s*I\/O|网络\s*IO|多线程|JVM|SpringBoot|Spring Boot|技术栈|工作职责|任职资格|岗位职责|职位描述|高可用|稳定性|服务端开发|后端开发|消息队列|工程项目|比赛中/.test(text)) return false;
    if (/^(网络|信息|数据|智能|软件)$/.test(text)) return false;
    if (/^[\u4e00-\u9fa5]{1,4}(网络|信息|数据|智能|软件)$/.test(text) && !/(公司|集团|有限|股份|科技)/.test(text)) return false;
    return true;
  }

  function extractCompanyNameFromRecruiterText(text) {
    const raw = String(text || '');
    const joinedLines = raw
      .split(/[\r\n]+/)
      .map(clean)
      .filter(Boolean)
      .concat([clean(raw)]);
    const companySuffix = '(?:集团|有限公司|股份有限公司|有限责任公司|科技有限公司|信息技术有限公司|软件有限公司|网络科技有限公司|智能科技有限公司|数据科技有限公司|公司)';
    const patterns = [
      /^(.{2,30}?(?:集团|有限公司|股份有限公司|有限责任公司|科技有限公司|信息技术有限公司|软件有限公司|网络科技有限公司|智能科技有限公司|数据科技有限公司|公司))\s*[·•]\s*(?:HR|hr|Hr|招聘|人事|招聘者)$/,
      /^(.{2,30}?(?:集团|有限公司|股份有限公司|有限责任公司|科技有限公司|信息技术有限公司|软件有限公司|网络科技有限公司|智能科技有限公司|数据科技有限公司|公司))\s+(?:HR|hr|Hr|招聘|人事|招聘者)$/,
      /(?:^|\s)(.{2,30}?(?:集团|有限公司|股份有限公司|有限责任公司|科技有限公司|信息技术有限公司|软件有限公司|网络科技有限公司|智能科技有限公司|数据科技有限公司|公司))\s*[·•]\s*(?:HR|hr|Hr|招聘|人事|招聘者)(?:\s|$)/
    ];
    for (const line of joinedLines) {
      for (const pattern of patterns) {
        const match = line.match(pattern);
        let candidate = match ? clean(match[1]) : '';
        const suffixMatch = candidate.match(new RegExp(`([A-Za-z0-9\\u4e00-\\u9fa5（）()·]{2,30}${companySuffix})$`));
        if (suffixMatch) candidate = clean(suffixMatch[1]);
        candidate = candidate.replace(/^(?:.{1,6}(?:女士|先生|老师|小姐)\s*)/, '');
        if (isValidCompanyName(candidate)) return candidate;
      }
    }
    return '';
  }

  function extractCompanyNameFromDetail(container) {
    const text = getElementText(container || findJobDetailContainer());
    const selectors = ['.company-name', '.company', '.boss-name + div', '[class*="companyName"]', '[class*="company-name"]', '[class*="company"]'];
    for (const selector of selectors) {
      const value = Array.from(document.querySelectorAll(selector))
        .map(getElementText)
        .map(value => extractCompanyNameFromRecruiterText(value) || value)
        .find(isValidCompanyName);
      if (value) return clean(value);
    }
    const recruiterCompany = extractCompanyNameFromRecruiterText(clean(document.body ? document.body.innerText : '') || text);
    if (recruiterCompany) return recruiterCompany;
    const match = text.match(/([\u4e00-\u9fa5A-Za-z0-9（）()·]{2,40}(?:集团|股份有限公司|有限责任公司|科技有限公司|信息技术有限公司|软件有限公司|网络科技有限公司|智能科技有限公司|数据科技有限公司|有限公司|公司))/);
    const candidate = match ? clean(match[1]) : '';
    return isValidCompanyName(candidate) ? candidate : '';
  }

  function extractCityFromMetaLine(text) {
    const match = clean(text).match(/(北京|天津|上海|广州|深圳|杭州|南京|苏州|成都|武汉|西安|重庆|厦门|合肥|长沙|远程)/);
    return match ? match[1] : '';
  }

  function parseExperience(text) {
    const match = clean(text).match(/(经验不限|在校\/应届|应届|1年以内|1-3年|3-5年|无需经验)/);
    return match ? match[1] : '';
  }

  function parseEducation(text) {
    const match = clean(text).match(/(本科|硕士|博士|大专|学历不限|统招本科)/);
    return match ? match[1] : '';
  }

  function parseScheduleAndDuration(text) {
    const source = clean(text);
    const scheduleMatch = source.match(/([1-7]\s*天\s*\/?\s*周|每周\s*[1-7]\s*天|周[一二三四五六日]\s*(?:至|-|到)\s*周[一二三四五六日])/);
    const durationMatch = source.match(/(\d+\s*个月|实习\s*\d+\s*个月|长期实习|至少\s*\d+\s*个月)/);
    return {
      schedule: scheduleMatch ? clean(scheduleMatch[1]) : '',
      duration: durationMatch ? clean(durationMatch[1]) : ''
    };
  }

  function extractTags(text) {
    return uniqueCaseInsensitive(clean(text).match(/[A-Za-z][A-Za-z0-9+.#-]{1,30}|[\u4e00-\u9fa5]{2,10}/g) || []);
  }

  function extractJobInfoFromDetail(container) {
    const detailContainer = container || findJobDetailContainer();
    const header = findJobHeaderBlock();
    const detailText = detailContainer ? getElementText(detailContainer) : '';
    const fallbackText = getVisibleJobText();
    const text = detailText || fallbackText;
    const scheduleDuration = parseScheduleAndDuration(text);
    const title = extractJobTitleFromHeader(header);
    const salary = extractSalaryFromHeader(header) || parseSalary(text);
    const city = extractCityFromMetaLine(getElementText(header)) || extractCityFromMetaLine(text);
    const companyName = extractCompanyNameFromDetail(detailContainer);

    return {
      jobTitle: title,
      companyName,
      salary,
      city,
      schedule: scheduleDuration.schedule,
      duration: scheduleDuration.duration,
      experience: parseExperience(text),
      education: parseEducation(text),
      tags: extractTags(text),
      jobText: text,
      jdText: text,
      sourceType: detailContainer ? 'detail-panel' : 'fallback-visible-page',
      sourceLength: text.length
    };
  }

  function matchKeywords(text, keywords) {
    const lower = clean(text).toLowerCase();
    return uniqueCaseInsensitive(keywords).filter(keyword => lower.includes(keyword.toLowerCase()));
  }

  function detectNonPrimaryStack(jobInfo) {
    return matchKeywords([jobInfo.jobTitle, jobInfo.jobText].join('\n'), ['Rust', 'C++', 'Go', 'Golang']);
  }

  function detectMatchedKeywords(jobInfo) {
    const text = [jobInfo.jobTitle, jobInfo.jobText].join('\n');
    return {
      roles: matchKeywords(text, activeScoringConfig.targetRoles),
      positive: matchKeywords(text, activeScoringConfig.positiveKeywords),
      java: matchKeywords(text, ['Java', 'Spring Boot', 'Spring Cloud', 'MyBatis', 'JVM']),
      ai: matchKeywords(text, ['AI应用', '大模型', 'RAG', 'Agent', 'LLM', 'Tool Calling', 'Prompt']),
      work: matchKeywords(text, ['接口开发', '后端服务', '模块开发', '数据库设计', '缓存', '联调', '问题排查'])
    };
  }

  function isOpsRiskContext(text) {
    return /运维工程师|系统运维|实施运维|驻场运维|运维岗|运维实习生|主要负责运维|Linux\s*运维岗位/.test(text);
  }

  function isOpsNeutralContext(text) {
    return /线上问题排查|稳定性保障|运维工具|运维平台开发|自动化运维系统开发|DevOps\s*平台开发|服务治理|可观测性|交易系统稳定性/.test(text);
  }

  function detectRiskFlags(jobInfo) {
    const riskText = jobInfo.sourceType === 'detail-panel'
      ? jobInfo.jobText
      : [jobInfo.jobTitle, jobInfo.jobText.slice(0, 1200)].join('\n');
    const hits = [];
    for (const keyword of uniqueCaseInsensitive(activeScoringConfig.negativeKeywords)) {
      if (keyword === '运维') {
        if (isOpsRiskContext(riskText)) hits.push(keyword);
        continue;
      }
      if (!riskText.toLowerCase().includes(keyword.toLowerCase())) continue;
      if (keyword === '销售' && /销售系统|销售数据/.test(riskText)) continue;
      if (keyword === '客服' && /客服平台/.test(riskText)) continue;
      if (keyword === '运营' && /运营平台/.test(riskText)) continue;
      hits.push(keyword);
    }
    return uniqueCaseInsensitive(hits);
  }

  function scoreDirection(jobInfo) {
    const text = [jobInfo.jobTitle, jobInfo.jobText].join('\n');
    const backendHits = matchKeywords(text, ['Java', '后端', '服务端', 'Spring Boot', '接口开发', '后端研发']);
    const aiHits = matchKeywords(text, ['AI应用', '大模型', 'RAG', 'Agent', 'LLM', 'Tool Calling']);
    return {
      score: clamp(backendHits.length * 5 + aiHits.length * 5, 0, 25),
      backendHits,
      aiHits,
      hits: uniqueCaseInsensitive(backendHits.concat(aiHits))
    };
  }

  function scoreJavaStack(jobInfo) {
    const hits = matchKeywords(jobInfo.jobText, ['Java', 'Spring Boot', 'Spring Cloud', 'MySQL', 'Redis', 'MyBatis', 'Linux', 'Docker']);
    return { score: clamp(hits.length * 4, 0, 18), hits };
  }

  function scoreAiStack(jobInfo) {
    const hits = matchKeywords(jobInfo.jobText, ['RAG', 'Agent', 'LLM', 'Tool Calling', 'Prompt', '大模型', 'AI应用']);
    return { score: clamp(hits.length * 3, 0, 10), hits };
  }

  function scoreWorkContent(jobInfo) {
    const hits = matchKeywords(jobInfo.jobText, ['接口开发', '后端服务', '模块开发', '数据库设计', '缓存', '联调', '问题排查']);
    return { score: clamp(hits.length * 2, 0, 8), hits };
  }

  function scoreActiveScoringConfig(jobInfo) {
    const hits = matchKeywords(jobInfo.jobText, activeScoringConfig.positiveKeywords);
    return { score: clamp(hits.length * 2, 0, 10), hits };
  }

  function getJavaInternFloor(jobInfo) {
    const text = [jobInfo.jobTitle, jobInfo.jobText].join('\n');
    if (/Java|Spring Boot|后端|服务端/.test(text) && /实习|Intern|校招/.test(text)) return 50;
    return 0;
  }

  function scoreInternship(jobInfo) {
    let score = 0;
    const cityHit = activeScoringConfig.preferredCities.some(city => jobInfo.city && jobInfo.city.includes(city));
    if (cityHit) score += 7;
    const days = (jobInfo.schedule || '').match(/[1-7]/);
    if (days && Number(days[0]) >= 4) score += 7;
    const months = (jobInfo.duration || '').match(/\d+/);
    if (/长期/.test(jobInfo.duration || '') || (months && Number(months[0]) >= 3)) score += 6;
    return clamp(score, 0, 20);
  }

  function scoreInfoCompleteness(jobInfo) {
    const fields = ['jobTitle', 'companyName', 'city', 'schedule', 'duration', 'salary'];
    return clamp(fields.filter(field => clean(jobInfo[field])).length * 2, 0, 10);
  }

  function missingFields(jobInfo) {
    const labels = {
      jobTitle: '岗位',
      companyName: '公司',
      salary: '薪资',
      city: '城市',
      schedule: '出勤',
      duration: '周期'
    };
    return Object.keys(labels).filter(key => !clean(jobInfo[key])).map(key => labels[key]);
  }

  function getConclusion(score, hardRejectHits) {
    if (hardRejectHits.length) return '明显不匹配';
    if (score >= 85) return '高匹配，建议 AI 复核';
    if (score >= 70) return '中高匹配，可 AI 复核';
    if (score >= 55) return '一般匹配，谨慎查看';
    if (score >= 40) return '低匹配，不建议优先分析';
    return '明显不匹配';
  }

  function buildRuleDetails(result) {
    const jobInfo = result.jobInfo;
    const breakdown = result.scoreBreakdown;
    const nonPrimary = result.nonPrimaryStackHints || [];
    const missing = result.missingFields || [];
    const backendHits = result.directionHits.backendHits || [];
    const aiHits = result.directionHits.aiHits || [];
    const javaHits = result.matchedKeywords.java || [];
    const aiTechHits = result.matchedKeywords.ai || [];

    return {
      roleReason: [
        backendHits.length ? `命中服务端/后端方向：${backendHits.join('、')}` : '未明显命中 Java/后端方向关键词',
        aiHits.length ? `命中 AI 应用方向：${aiHits.join('、')}` : '未明显命中 AI 应用关键词',
        nonPrimary.length ? `岗位主方向可能偏 ${nonPrimary.join(' / ')}，建议 AI 复核语言栈匹配度` : ''
      ].filter(Boolean).join('；'),
      techReason: [
        javaHits.length ? `命中 Java 技术栈：${javaHits.join('、')}` : '未明显命中 Spring Boot、Redis、MySQL 等用户主项目技术栈',
        aiTechHits.length ? `命中 AI 技术栈：${aiTechHits.join('、')}` : '',
        nonPrimary.length ? `非主技术栈提示：${nonPrimary.join(' / ')}` : ''
      ].filter(Boolean).join('；'),
      internshipReason: [
        jobInfo.city ? `城市：${jobInfo.city}${activeScoringConfig.preferredCities.includes(jobInfo.city) ? '，符合目标城市' : '，需确认是否匹配'}` : '未识别城市',
        jobInfo.schedule ? `出勤：${jobInfo.schedule}` : '未识别出勤，建议人工确认',
        jobInfo.duration ? `时长：${jobInfo.duration}` : '未识别周期，建议人工确认'
      ].join('；'),
      riskReason: result.riskFlags.length
        ? `命中风险词：${result.riskFlags.join('、')}`
        : '暂无明显强风险词；运维平台开发、稳定性保障、线上问题排查不作为强风险',
      infoReason: missing.length
        ? `已识别：${['岗位', '公司', '薪资', '城市', '出勤', '周期'].filter(label => !missing.includes(label)).join('、') || '无'}；未识别：${missing.join('、')}。字段识别不完整，建议以 AI 分析和人工查看为准。`
        : '岗位、公司、薪资、城市、出勤和周期均已识别。',
      nonPrimaryStackHints: nonPrimary,
      missingFields: missing,
      localRuleNote: '本地规则用于判断是否值得 AI 复核，不是最终投递决策；最终投递建议以 AI 深度分析结果为准。'
    };
  }

  function buildReason(scoreResult) {
    if (scoreResult.hardRejectHits.length) return `命中硬性排除词：${scoreResult.hardRejectHits.join(', ')}`;
    if (scoreResult.riskFlags.length) return `技术方向有命中，但存在风险词需复核：${scoreResult.riskFlags.join(', ')}`;
    if (scoreResult.nonPrimaryStackHints.length) return `识别到非主技术栈：${scoreResult.nonPrimaryStackHints.join(' / ')}，建议 AI 复核语言栈匹配度。`;
    const positives = uniqueCaseInsensitive(scoreResult.positiveHits.concat(scoreResult.targetHits));
    return positives.length ? `命中岗位方向/技术栈：${positives.slice(0, 8).join(', ')}` : '基于岗位详情可见文本的规则初筛，建议结合 AI 分析再判断。';
  }

  function scoreJob(jobInfo) {
    const matchedKeywords = detectMatchedKeywords(jobInfo);
    const direction = scoreDirection(jobInfo);
    const javaStack = scoreJavaStack(jobInfo);
    const aiStack = scoreAiStack(jobInfo);
    const workContent = scoreWorkContent(jobInfo);
    const activeConfig = scoreActiveScoringConfig(jobInfo);
    const riskFlags = detectRiskFlags(jobInfo);
    const hardRejectHits = matchKeywords(jobInfo.jobText, activeScoringConfig.hardRejectKeywords);
    const nonPrimaryStackHints = detectNonPrimaryStack(jobInfo);

    const roleScore = direction.score;
    let techScore = clamp(javaStack.score + aiStack.score + Math.min(workContent.score, 5) + Math.min(activeConfig.score, 4), 0, 25);
    if (nonPrimaryStackHints.length && !javaStack.hits.length) techScore = Math.max(0, techScore - 3);
    const internshipScore = scoreInternship(jobInfo);
    const riskPenalty = riskFlags.length * (jobInfo.sourceType === 'detail-panel' ? 5 : 2);
    const riskScore = hardRejectHits.length ? 0 : clamp(20 - riskPenalty, 0, 20);
    const infoScore = scoreInfoCompleteness(jobInfo);
    const floor = getJavaInternFloor(jobInfo);
    let score = roleScore + techScore + internshipScore + riskScore + infoScore;
    score = Math.max(score, floor);
    if (hardRejectHits.length) score = Math.min(score, 35);
    score = clamp(Math.round(score), 0, 100);

    const result = {
      jobInfo,
      score,
      finalScore: score,
      rawScore: score,
      conclusion: getConclusion(score, hardRejectHits),
      direction: direction.aiHits.length ? 'Java 后端 / AI 应用' : 'Java 后端',
      excelTier: score >= 70 ? 'A' : (score >= 55 ? 'B' : 'C'),
      matchedKeywords,
      directionHits: direction,
      targetHits: matchedKeywords.roles,
      positiveHits: uniqueCaseInsensitive(matchedKeywords.positive.concat(javaStack.hits, aiStack.hits, workContent.hits)),
      negativeHits: riskFlags,
      hardRejectHits,
      riskFlags,
      nonPrimaryStackHints,
      missingFields: missingFields(jobInfo),
      scoreBreakdown: { roleScore, techScore, internshipScore, riskScore, infoScore },
      sourceType: jobInfo.sourceType,
      ruleInfo: {
        configScore: activeConfig.score,
        floorRule: floor ? 'Java 实习保底' : '无',
        hardRule: hardRejectHits.join(', ') || '无',
        scheduleRaw: jobInfo.schedule || '未识别',
        durationRaw: jobInfo.duration || '未识别'
      }
    };
    result.reason = buildReason(result);
    result.ruleDetails = buildRuleDetails(result);
    return result;
  }

  function buildAiAnalyzePayload(jobInfo, scoreResult) {
    return {
      jobTitle: jobInfo.jobTitle,
      companyName: jobInfo.companyName,
      salary: jobInfo.salary,
      city: jobInfo.city,
      schedule: jobInfo.schedule,
      duration: jobInfo.duration,
      jobText: jobInfo.jobText,
      jdText: jobInfo.jdText,
      ruleScore: scoreResult.score,
      ruleConclusion: scoreResult.conclusion,
      matchedKeywords: uniqueCaseInsensitive(scoreResult.positiveHits.concat(scoreResult.targetHits)),
      riskFlags: scoreResult.riskFlags
    };
  }

  function callAiAnalyzeBackend(payload) {
    return requestJson({ method: 'POST', url: `${BACKEND_BASE_URL}/api/job/analyze`, body: payload, timeout: 45000 });
  }

  function renderCompactList(items, emptyText) {
    if (!Array.isArray(items) || !items.length) return `<div style="color:#6b7280;">${escapeHtml(emptyText)}</div>`;
    return `<ul style="padding-left:18px;margin:4px 0;">${items.slice(0, 6).map(item => `<li>${escapeHtml(item)}</li>`).join('')}</ul>`;
  }

  function renderProfileRagEvidence(profileRag) {
    if (!profileRag) return '';
    const chunks = Array.isArray(profileRag.chunks) ? profileRag.chunks : [];
    return `
      <details style="margin-top:8px;">
        <summary style="cursor:pointer;font-weight:700;">Profile RAG-Lite Evidence (${escapeHtml(chunks.length)})</summary>
        ${chunks.length ? chunks.map(chunk => `
          <div style="margin-top:6px;padding-top:6px;border-top:1px solid #e5e7eb;">
            <b>${escapeHtml(chunk.title || 'chunk')}</b>
            <span style="color:#6b7280;"> score ${escapeHtml(chunk.score == null ? 0 : chunk.score)} / ${escapeHtml(chunk.sourceType || '')}</span>
            <div style="color:#4b5563;">${escapeHtml(compactText(chunk.content || '', 160))}</div>
          </div>
        `).join('') : '<div style="margin-top:6px;color:#6b7280;">未命中用户画像证据。</div>'}
      </details>
    `;
  }

  function renderAiAnalyzeResult(result, error, loading) {
    if (loading) return '<div style="margin-top:8px;padding:8px;border:1px solid #e5e7eb;background:#f9fafb;border-radius:8px;color:#6b7280;">正在调用本地后端进行 AI 深度核验...</div>';
    if (error) return `<div style="margin-top:8px;padding:8px;border:1px solid #fed7aa;background:#fff7ed;border-radius:8px;color:#c2410c;">${escapeHtml(error)}</div>`;
    if (!result) return '<div style="margin-top:8px;color:#6b7280;font-size:12px;">AI 深度核验仅在手动点击后调用本地后端。</div>';
    return `
      <details open style="margin-top:8px;padding:8px;border:1px solid #e5e7eb;background:#f9fafb;border-radius:8px;">
        <summary style="cursor:pointer;font-weight:700;">AI 分析结果</summary>
        <div style="margin-top:8px;">
          <div><b>AI 决策:</b> ${escapeHtml(result.decision || '')}</div>
          <div><b>AI 分数:</b> ${escapeHtml(result.score == null ? '' : result.score)}</div>
          <div><b>方向:</b> ${escapeHtml(result.direction || '')}</div>
          <div style="margin-top:6px;"><b>Reasons</b>${renderCompactList(result.reasons, '暂无')}</div>
          <div style="margin-top:6px;"><b>Risks</b>${renderCompactList(result.risks, '暂无')}</div>
          <div style="margin-top:6px;"><b>Resume Matches</b>${renderCompactList(result.resumeMatches, '暂无')}</div>
          <div style="margin-top:6px;"><b>Interview Focus</b>${renderCompactList(result.interviewFocus, '暂无')}</div>
          <div style="margin-top:6px;"><b>Suggested Message:</b> ${escapeHtml(result.suggestedMessage || '')}</div>
          ${renderProfileRagEvidence(result.profileRag)}
        </div>
      </details>
    `;
  }

  async function loadJobHistoryForResult(result, force) {
    if (!result || !result.jobInfo) return;
    const key = `${result.jobInfo.companyName || ''}|${result.jobInfo.jobTitle || ''}`;
    if (!force && window.jobFitHistoryKey === key) return;
    window.jobFitHistoryKey = key;
    jobFitHistoryLoading = true;
    jobFitHistoryError = '';
    try {
      const params = new URLSearchParams();
      if (result.jobInfo.companyName) params.set('companyName', result.jobInfo.companyName);
      if (result.jobInfo.jobTitle) params.set('jobTitle', result.jobInfo.jobTitle);
      jobFitHistoryRecords = await requestJson({ url: `${BACKEND_BASE_URL}/api/jobs/match?${params.toString()}`, timeout: 8000 }) || [];
    } catch (e) {
      jobFitHistoryRecords = [];
      jobFitHistoryError = '后端历史记录接口未启用或不可用';
    } finally {
      jobFitHistoryLoading = false;
      if (lastScoreResult) renderJobFitPanel(lastScoreResult);
    }
  }

  function renderJobHistoryPanel() {
    if (jobFitHistoryLoading) return '<div style="margin-top:8px;color:#6b7280;font-size:12px;">正在查询历史记录...</div>';
    if (jobFitHistoryError) return `<div style="margin-top:8px;color:#9ca3af;font-size:12px;">${escapeHtml(jobFitHistoryError)}</div>`;
    const records = Array.isArray(jobFitHistoryRecords) ? jobFitHistoryRecords.slice(0, 3) : [];
    if (!records.length) return '';
    return `
      <details style="margin-top:8px;padding:8px;border-radius:8px;background:#f8fafc;border:1px solid #e5e7eb;">
        <summary style="cursor:pointer;font-weight:700;">历史记录</summary>
        ${records.map(record => `
          <div style="padding:6px 0;border-top:1px solid #eef2f7;">
            <div><b>${escapeHtml(record.companyName || '未知公司')}</b> / ${escapeHtml(record.jobTitle || '未知岗位')}</div>
            <div style="color:#6b7280;">AI: ${escapeHtml(record.aiDecision || '未记录')} / ${escapeHtml(record.aiScore == null ? '未记录' : record.aiScore)}</div>
          </div>
        `).join('')}
      </details>
    `;
  }

  function defaultFeedbackDraft() {
    return { applyStatus: '未投递', chatStatus: '未沟通', interviewStatus: '未约面', feedbackNote: '', rejectReason: '' };
  }

  function resetFeedbackDraft() {
    jobFitFeedbackDraft = defaultFeedbackDraft();
  }

  function feedbackSelect(id, label, value, options) {
    return `
      <label style="display:block;margin:6px 0 3px;font-weight:600;">${escapeHtml(label)}</label>
      <select id="${id}" style="width:100%;box-sizing:border-box;padding:6px;border:1px solid #d1d5db;border-radius:6px;">
        ${options.map(option => `<option value="${escapeHtml(option)}" ${option === value ? 'selected' : ''}>${escapeHtml(option)}</option>`).join('')}
      </select>
    `;
  }

  function updateFeedbackDraftFromDom() {
    const ids = {
      applyStatus: 'job-fit-apply-status',
      chatStatus: 'job-fit-chat-status',
      interviewStatus: 'job-fit-interview-status',
      feedbackNote: 'job-fit-feedback-note',
      rejectReason: 'job-fit-reject-reason'
    };
    for (const [key, id] of Object.entries(ids)) {
      const el = document.getElementById(id);
      if (el) jobFitFeedbackDraft[key] = clean(el.value);
    }
  }

  function bindFeedbackDraftEvents() {
    ['job-fit-apply-status', 'job-fit-chat-status', 'job-fit-interview-status', 'job-fit-feedback-note', 'job-fit-reject-reason'].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.oninput = updateFeedbackDraftFromDom;
    });
  }

  function renderFeedbackStatus() {
    if (jobFitFeedbackSaving) return '<div style="margin-top:6px;color:#6b7280;">正在保存反馈...</div>';
    if (jobFitFeedbackSaved) return '<div style="margin-top:6px;color:#16a34a;">反馈已保存。</div>';
    if (jobFitFeedbackError) return `<div style="margin-top:6px;color:#c2410c;">${escapeHtml(jobFitFeedbackError)}</div>`;
    return '';
  }

  function renderFeedbackPanel(aiResult) {
    if (!aiResult) return '';
    return `
      <details style="margin-top:8px;padding:8px;border-radius:8px;background:#f8fafc;border:1px solid #e5e7eb;">
        <summary style="cursor:pointer;font-weight:700;">投递反馈</summary>
        ${feedbackSelect('job-fit-apply-status', '投递状态', jobFitFeedbackDraft.applyStatus, ['未投递', '已投递', '暂不投递', '放弃'])}
        ${feedbackSelect('job-fit-chat-status', '沟通状态', jobFitFeedbackDraft.chatStatus, ['未沟通', '已沟通', '已读未回', '有回复'])}
        ${feedbackSelect('job-fit-interview-status', '面试状态', jobFitFeedbackDraft.interviewStatus, ['未约面', '已约面', '已面试', '通过', '未通过'])}
        <label style="display:block;margin:6px 0 3px;font-weight:600;">备注</label>
        <textarea id="job-fit-feedback-note" style="width:100%;height:56px;box-sizing:border-box;border:1px solid #d1d5db;border-radius:6px;">${escapeHtml(jobFitFeedbackDraft.feedbackNote)}</textarea>
        <label style="display:block;margin:6px 0 3px;font-weight:600;">放弃/拒绝原因</label>
        <input id="job-fit-reject-reason" value="${escapeHtml(jobFitFeedbackDraft.rejectReason)}" style="width:100%;box-sizing:border-box;padding:6px;border:1px solid #d1d5db;border-radius:6px;" />
        <button id="job-fit-feedback-save" style="width:100%;margin-top:8px;border:none;background:#2563eb;color:#fff;border-radius:8px;padding:8px 10px;cursor:pointer;font-weight:700;">保存反馈</button>
        ${renderFeedbackStatus()}
      </details>
    `;
  }

  function buildFeedbackPayload(aiResult) {
    updateFeedbackDraftFromDom();
    return Object.assign({
      jobRecordId: aiResult && aiResult.jobRecordId,
      taskId: aiResult && aiResult.taskId
    }, jobFitFeedbackDraft);
  }

  function callSaveJobFeedback(payload) {
    return requestJson({ method: 'POST', url: `${BACKEND_BASE_URL}/api/job/feedback`, body: payload, timeout: 15000 });
  }

  function copyText(text) {
    try {
      GM_setClipboard(text);
      return true;
    } catch (e) {
      return false;
    }
  }

  function renderTagList(items, emptyText) {
    const values = uniqueCaseInsensitive(items || []);
    if (!values.length) return `<span style="color:#6b7280;">${escapeHtml(emptyText)}</span>`;
    return values.slice(0, 12).map(item => `<span style="display:inline-block;margin:2px 4px 2px 0;padding:2px 6px;border-radius:999px;background:#f3f4f6;color:#374151;">${escapeHtml(item)}</span>`).join('');
  }

  function renderScoreBreakdown(result) {
    const b = result.scoreBreakdown;
    return `
      <div style="margin:8px 0;padding:8px;border-radius:8px;background:#f9fafb;border:1px solid #e5e7eb;">
        <div><b>规则初筛：</b>${escapeHtml(result.score)}</div>
        <div>方向匹配：${escapeHtml(b.roleScore)}/25</div>
        <div>技术匹配：${escapeHtml(b.techScore)}/25</div>
        <div>实习条件：${escapeHtml(b.internshipScore)}/20</div>
        <div>风险控制：${escapeHtml(b.riskScore)}/20</div>
        <div>信息完整度：${escapeHtml(b.infoScore)}/10</div>
      </div>
    `;
  }

  function renderRuleScreeningDetails(result) {
    const d = result.ruleDetails || buildRuleDetails(result);
    return `
      <details style="margin-top:8px;padding:8px;border-radius:8px;background:#f8fafc;border:1px solid #e5e7eb;">
        <summary style="cursor:pointer;font-weight:700;">规则初筛详情</summary>
        <div style="margin-top:8px;">
          <div style="margin-bottom:6px;"><b>方向匹配：</b>${escapeHtml(d.roleReason)}</div>
          <div style="margin-bottom:6px;"><b>技术匹配：</b>${escapeHtml(d.techReason)}</div>
          <div style="margin-bottom:6px;"><b>实习条件：</b>${escapeHtml(d.internshipReason)}</div>
          <div style="margin-bottom:6px;"><b>风险说明：</b>${escapeHtml(d.riskReason)}</div>
          <div style="margin-bottom:6px;"><b>信息完整度：</b>${escapeHtml(d.infoReason)}</div>
          <div style="color:#6b7280;">${escapeHtml(d.localRuleNote)}</div>
        </div>
      </details>
    `;
  }

  function buildCopyText(result) {
    const d = result.ruleDetails || buildRuleDetails(result);
    return [
      `岗位：${result.jobInfo.jobTitle || '未识别'}`,
      `公司：${result.jobInfo.companyName || '未识别'}`,
      `薪资：${result.jobInfo.salary || '未识别'}`,
      `城市：${result.jobInfo.city || '未识别'}`,
      `规则初筛：${result.score}`,
      `初筛结论：${result.conclusion}`,
      `方向匹配：${d.roleReason}`,
      `技术匹配：${d.techReason}`,
      `实习条件：${d.internshipReason}`,
      `风险说明：${d.riskReason}`,
      `信息完整度：${d.infoReason}`,
      `本地规则说明：${d.localRuleNote}`
    ].join('\n');
  }

  function renderJobFitPanel(result) {
    if (!result) return;
    const resultKey = JSON.stringify([result.jobInfo.jobTitle, result.jobInfo.companyName, result.jobInfo.salary, result.jobInfo.city, result.jobInfo.jobText.slice(0, 100)]);
    if (resultKey !== lastResultKey) {
      jobFitAiLoading = false;
      jobFitAiResult = null;
      jobFitAiError = '';
      jobFitFeedbackSaving = false;
      jobFitFeedbackSaved = false;
      jobFitFeedbackError = '';
      resetFeedbackDraft();
      lastResultKey = resultKey;
    }
    lastScoreResult = result;
    let panel = document.getElementById(PANEL_ID);
    if (!panel) {
      panel = document.createElement('div');
      panel.id = PANEL_ID;
      panel.style.cssText = `
        position: fixed;
        right: 20px;
        bottom: 24px;
        width: 340px;
        max-height: 74vh;
        overflow: auto;
        background: #fff;
        color: #111827;
        z-index: 999998;
        border: 1px solid #e5e7eb;
        box-shadow: 0 8px 28px rgba(0,0,0,.16);
        border-radius: 10px;
        font-size: 13px;
        line-height: 1.45;
      `;
      document.body.appendChild(panel);
    }
    const color = result.score >= 85 ? '#16a34a' : (result.score >= 70 ? '#2563eb' : (result.score >= 55 ? '#7c3aed' : '#6b7280'));
    const sourceMessage = result.sourceType === 'detail-panel' ? '已识别右侧岗位详情' : '结果可能受页面列表干扰';
    const fieldWarning = result.missingFields.length ? '<div style="margin:8px 0;color:#b45309;font-size:12px;">字段识别不完整，建议以 AI 分析和人工查看为准。</div>' : '';
    panel.innerHTML = `
      <div style="display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border-bottom:1px solid #e5e7eb;">
        <div style="font-weight:700;">AI Job Screening Agent</div>
        <button id="job-fit-toggle" style="border:none;background:#f3f4f6;border-radius:6px;padding:3px 8px;cursor:pointer;">${jobFitCollapsed ? '展开' : '收起'}</button>
      </div>
      <div id="job-fit-body" style="display:${jobFitCollapsed ? 'none' : 'block'};padding:12px;">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;">
          <span style="font-size:17px;font-weight:700;color:${color};">${escapeHtml(result.conclusion)}</span>
          <span style="font-size:22px;font-weight:800;color:${color};">${escapeHtml(result.score)}</span>
        </div>
        <div style="margin-bottom:8px;padding:6px 8px;border-radius:8px;background:#f9fafb;color:${result.sourceType === 'detail-panel' ? '#16a34a' : '#f97316'};font-size:12px;">${escapeHtml(sourceMessage)}</div>
        <div style="margin-bottom:8px;padding:6px 8px;border-radius:8px;background:${scoringConfigSource.startsWith('backend') ? '#ecfdf5' : '#f9fafb'};color:${scoringConfigSource.startsWith('backend') ? '#047857' : '#6b7280'};font-size:12px;">评分配置：${escapeHtml(scoringConfigStatusText)}</div>
        <div style="margin-bottom:6px;"><b>岗位：</b>${escapeHtml(result.jobInfo.jobTitle || '未识别')}</div>
        <div style="margin-bottom:6px;"><b>公司：</b>${escapeHtml(result.jobInfo.companyName || '未识别')}</div>
        <div style="margin-bottom:6px;"><b>薪资：</b>${escapeHtml(result.jobInfo.salary || '未识别')}</div>
        <div style="margin-bottom:6px;"><b>城市：</b>${escapeHtml(result.jobInfo.city || '未识别')}</div>
        <div style="margin-bottom:6px;"><b>周期：</b>${escapeHtml([result.jobInfo.schedule, result.jobInfo.duration].filter(Boolean).join(' / ') || '未识别')}</div>
        <div style="margin-bottom:6px;"><b>学历/经验：</b>${escapeHtml([result.jobInfo.education, result.jobInfo.experience].filter(Boolean).join(' / ') || '未识别')}</div>
        ${fieldWarning}
        ${renderScoreBreakdown(result)}
        ${renderRuleScreeningDetails(result)}
        <div style="margin:8px 0 4px;"><b>命中关键词：</b></div>
        <div>${renderTagList(result.positiveHits.concat(result.targetHits), '暂无明显技术关键词')}</div>
        <div style="margin:8px 0 4px;"><b>风险关键词：</b></div>
        <div>${renderTagList(result.riskFlags.concat(result.hardRejectHits), '暂无明显风险')}</div>
        <div style="margin:10px 0 8px;"><b>评分理由：</b>${escapeHtml(result.reason)}</div>
        <button id="job-fit-copy" style="width:100%;border:none;background:${color};color:#fff;border-radius:8px;padding:8px 10px;cursor:pointer;font-weight:700;">复制岗位分析</button>
        <button id="job-fit-ai-analyze" ${jobFitAiLoading ? 'disabled' : ''} style="width:100%;margin-top:8px;border:none;background:#111827;color:#fff;border-radius:8px;padding:8px 10px;cursor:${jobFitAiLoading ? 'not-allowed' : 'pointer'};font-weight:700;opacity:${jobFitAiLoading ? '.65' : '1'};">${jobFitAiLoading ? '分析中...' : 'AI 深度核验'}</button>
        <div id="job-fit-copy-tip" style="margin-top:6px;color:#6b7280;font-size:12px;"></div>
        <div id="job-fit-ai-result">
          ${renderJobHistoryPanel()}
          ${renderAiAnalyzeResult(jobFitAiResult, jobFitAiError, jobFitAiLoading)}
          ${renderFeedbackPanel(jobFitAiResult)}
        </div>
        <div style="margin-top:10px;color:#6b7280;font-size:12px;border-top:1px solid #e5e7eb;padding-top:8px;">本地规则仅用于初筛，最终投递建议以 AI 分析结果为准。仅分析当前页面可见文本，不自动投递，不自动发送消息。</div>
      </div>
    `;
    bindFeedbackDraftEvents();
    loadJobHistoryForResult(result, false);
    document.getElementById('job-fit-toggle').onclick = () => {
      jobFitCollapsed = !jobFitCollapsed;
      localStorage.setItem(STORAGE_COLLAPSED_KEY, String(jobFitCollapsed));
      renderJobFitPanel(lastScoreResult);
    };
    document.getElementById('job-fit-copy').onclick = () => {
      document.getElementById('job-fit-copy-tip').textContent = copyText(buildCopyText(result)) ? '已复制到剪贴板。' : '复制失败，请手动复制。';
    };
    document.getElementById('job-fit-ai-analyze').onclick = async () => {
      if (jobFitAiLoading || !lastScoreResult) return;
      jobFitAiLoading = true;
      jobFitAiResult = null;
      jobFitAiError = '';
      jobFitFeedbackSaved = false;
      jobFitFeedbackError = '';
      resetFeedbackDraft();
      renderJobFitPanel(lastScoreResult);
      try {
        const payload = buildAiAnalyzePayload(lastScoreResult.jobInfo, lastScoreResult);
        jobFitAiResult = await callAiAnalyzeBackend(payload);
      } catch (e) {
        jobFitAiError = '后端未启动或 /api/job/analyze 调用失败，请确认本地服务运行在 http://localhost:8080。';
      } finally {
        jobFitAiLoading = false;
        renderJobFitPanel(lastScoreResult);
      }
    };
    const feedbackBtn = document.getElementById('job-fit-feedback-save');
    if (feedbackBtn) {
      feedbackBtn.onclick = async () => {
        if (jobFitFeedbackSaving || !jobFitAiResult) return;
        const payload = buildFeedbackPayload(jobFitAiResult);
        if (!payload.jobRecordId && !payload.taskId) {
          jobFitFeedbackError = '缺少 jobRecordId 或 taskId，无法保存反馈。';
          renderJobFitPanel(lastScoreResult);
          return;
        }
        jobFitFeedbackSaving = true;
        jobFitFeedbackError = '';
        jobFitFeedbackSaved = false;
        renderJobFitPanel(lastScoreResult);
        try {
          await callSaveJobFeedback(payload);
          jobFitFeedbackSaved = true;
          loadJobHistoryForResult(lastScoreResult, true);
        } catch (e) {
          jobFitFeedbackError = '反馈保存失败，请确认后端接口已启用。';
        } finally {
          jobFitFeedbackSaving = false;
          renderJobFitPanel(lastScoreResult);
        }
      };
    }
  }

  function updateJobFitPanel() {
    const detailContainer = findJobDetailContainer();
    const jobInfo = extractJobInfoFromDetail(detailContainer);
    if (!jobInfo.jobText || jobInfo.jobText.length < 40) return;
    renderJobFitPanel(scoreJob(jobInfo));
  }

  function observeJobDetailChanges() {
    const observer = new MutationObserver(mutations => {
      const onlyPanel = Array.isArray(mutations) && mutations.length && mutations.every(mutation => {
        const target = mutation.target;
        const el = target && (target.nodeType === Node.ELEMENT_NODE ? target : target.parentElement);
        return Boolean(el && el.closest && el.closest(`#${PANEL_ID}`));
      });
      if (onlyPanel) return;
      clearTimeout(updateTimer);
      updateTimer = setTimeout(updateJobFitPanel, 500);
    });
    observer.observe(document.body, { childList: true, subtree: true, characterData: true });
  }

  loadScoringConfigFromBackend().finally(() => {
    setTimeout(updateJobFitPanel, 1200);
    observeJobDetailChanges();
  });

  window.JobFitScoring = {
    cleanJobTitle,
    parseSalary,
    extractSalaryFromHeader,
    collectVisibleSalaryCandidates,
    isLocationLike,
    isValidCompanyName,
    extractCompanyNameFromRecruiterText,
    extractCompanyNameFromDetail,
    detectRiskFlags,
    detectNonPrimaryStack,
    buildRuleDetails,
    renderRuleScreeningDetails,
    scoreJob,
    buildAiAnalyzePayload,
    callAiAnalyzeBackend,
    loadJobHistoryForResult,
    renderFeedbackPanel
  };
})();

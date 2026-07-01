// ==UserScript==
// @name         Job Chat Status Export Helper
// @namespace    job-chat-status-export-helper
// @version      1.3.1
// @description  Export visible job chat status from the current browser page into TSV format for personal job-search follow-up.
// @match        https://www.zhipin.com/*
// @run-at       document-end
// @grant        GM_setClipboard
// @grant        GM_xmlhttpRequest
// @connect      localhost
// @connect      127.0.0.1
// ==/UserScript==

(function () {
  'use strict';

  /**
   * Job Chat Status Export Helper
   *
   * 定位：个人求职信息整理工具。
   * 只读取当前登录用户在网页聊天列表里已经能看到的 DOM 文本。
   * 仅用于读取页面可见文本、整理页面已展示状态、导出 TSV 和本地辅助分析。
   * 不访问非公开接口、不处理验证码或登录校验、不执行投递或消息发送操作。
   *
   * Job Fit Scoring / 岗位匹配度实时评分：
   * 仅基于当前页面可见文本进行本地规则评分，不访问非公开接口。
   * 不绕过验证码，不自动投递，不自动发送消息。
   * 评分只作为个人求职跟进参考，最终是否投递由用户人工决定。
   */

  const MAX_SCROLLS = 12; // 想读取更多联系人，可以改成 20。建议低频手动使用，不要高频循环。

  const ROLE_WORDS = [
    '人事行政主管',
    '人力资源主管',
    '人力资源HR',
    '人力主管',
    'HRBP专员',
    '招聘人员',
    '招聘主管',
    '招聘专员',
    '招聘者',
    '招聘官',
    '人事专员',
    '人事主管',
    '人事经理',
    '人事行政',
    '人力总监',
    '公司负责人',
    '区域经理',
    '总经理',
    '架构师',
    'HRBP',
    'HRM',
    'hrbp',
    'CEO',
    'HR',
    '人事'
  ].sort((a, b) => b.length - a.length);

  // 只用于在无空格文本里辅助判断姓名和公司边界，不需要覆盖所有公司。
  // 这里保留通用城市、行业和组织名前缀，避免在公开仓库中放入真实沟通样本。
  const COMPANY_START_HINTS = [
    '北京', '上海', '深圳', '广州', '杭州', '天津', '南京', '苏州',
    '成都', '武汉', '西安', '重庆', '厦门', '青岛', '合肥',
    '中', '国', '华', '新', '云', '智', '数', '科', '网',
    '信息', '科技', '智能', '网络', '软件', '数据', '云计算',
    '示例', '样例', '测试', '某某'
  ];

  const MSG_START_RE = /^(感谢|您好|你好|很遗憾|不好意思|抱歉|方便|可以|请问|这边|目前|简历|先发|稍等|我们|岗位|什么时候|收到|已收到|发我|加微信|电话|面试)/;

  const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

  const clean = (s) => (s || '')
    .replace(/&#92;/g, '')
    .replace(/\\$/g, '')
    .replace(/…/g, '...')
    .replace(/\s+/g, ' ')
    .trim();

  const compact = (s) => clean(s).replace(/[|\s]+/g, '');

  function escapeRegExp(s) {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  function displayRole(role) {
    const low = role.toLowerCase();
    if (low === 'hr') return 'HR';
    if (low === 'hrbp') return 'HRBP';
    if (low === 'hrm') return 'HRM';
    if (low === 'ceo') return 'CEO';
    return role;
  }

  function startsWithAny(s, arr) {
    return arr.some(x => s.startsWith(x));
  }

  function findRoleAtEnd(headerCompact) {
    const low = headerCompact.toLowerCase();

    for (const role of ROLE_WORDS) {
      const rLow = role.toLowerCase();
      if (low.endsWith(rLow)) {
        return {
          role: displayRole(role),
          left: headerCompact.slice(0, headerCompact.length - role.length)
        };
      }
    }

    return {
      role: '',
      left: headerCompact
    };
  }

  function splitNameCompany(leftCompact) {
    leftCompact = compact(leftCompact);

    if (!leftCompact) {
      return { name: '', company: '' };
    }

    // 例：张女士示例科技 / 李先生样例网络
    const honorMatch = leftCompact.match(/^(.{1,6}?(女士|先生|小姐|老师))(.+)$/);
    if (honorMatch) {
      return {
        name: honorMatch[1],
        company: honorMatch[3]
      };
    }

    // 无“先生/女士”的情况：张三示例科技、李四测试智能
    for (const nameLen of [4, 3, 2]) {
      if (leftCompact.length > nameLen) {
        const possibleCompany = leftCompact.slice(nameLen);
        if (startsWithAny(possibleCompany, COMPANY_START_HINTS)) {
          return {
            name: leftCompact.slice(0, nameLen),
            company: possibleCompany
          };
        }
      }
    }

    // 兜底：多数中文姓名是 3 个字。身份字段偶尔不准不影响核心判断。
    if (leftCompact.length > 3) {
      return {
        name: leftCompact.slice(0, 3),
        company: leftCompact.slice(3)
      };
    }

    return {
      name: leftCompact,
      company: ''
    };
  }

  function parseHeader(headerText) {
    const headerCompact = compact(headerText);

    if (!headerCompact) {
      return {
        name: '',
        company: '',
        role: ''
      };
    }

    const roleInfo = findRoleAtEnd(headerCompact);
    const nameCompany = splitNameCompany(roleInfo.left);

    return {
      name: nameCompany.name,
      company: nameCompany.company,
      role: roleInfo.role
    };
  }

  function roleBoundaryPattern(role) {
    // 允许“人力资源 HR”这种中间有空格的情况。
    return role.split('').map(escapeRegExp).join('\\s*');
  }

  function splitByRoleBoundary(body) {
    const candidates = [];

    for (const role of ROLE_WORDS) {
      const re = new RegExp(roleBoundaryPattern(role), 'gi');
      let match;

      while ((match = re.exec(body)) !== null) {
        const end = match.index + match[0].length;
        const before = clean(body.slice(0, end));
        const after = clean(body.slice(end));

        if (before.length < 3 || before.length > 80) continue;

        // role 后面如果直接进入回复内容，就认为这里是“头部信息”和“最后消息”的分界。
        if (!after || MSG_START_RE.test(after) || after.startsWith('[')) {
          candidates.push({
            end,
            role,
            roleLen: role.length
          });
        }
      }
    }

    if (!candidates.length) return null;

    candidates.sort((a, b) => {
      if (a.end !== b.end) return a.end - b.end;
      return b.roleLen - a.roleLen;
    });

    const best = candidates[0];

    return {
      head: clean(body.slice(0, best.end)),
      msg: clean(body.slice(best.end))
    };
  }

  function fallbackSplitMessage(body) {
    const m = body.match(/\s(感谢|您好|你好|很遗憾|不好意思|抱歉|方便|可以|请问|这边|目前|简历|先发|稍等|我们|岗位|什么时候|收到|已收到|发我|加微信|电话|面试)/);

    if (!m) {
      return {
        head: body,
        msg: ''
      };
    }

    return {
      head: clean(body.slice(0, m.index)),
      msg: clean(body.slice(m.index))
    };
  }

  function buildAction(status, raw) {
    const text = raw || '';

    // 明确拒绝。
    if (/很遗憾|不合适|不匹配|不能与您共事|暂时不考虑|不太符合|暂无合适|祝您.*找到/.test(text)) {
      return {
        actionLevel: 'P9',
        nextStep: '拒绝/不用管'
      };
    }

    // 对方主动回复，且不是拒绝。
    if (status === '对方回复/无状态') {
      if (/方便|可以|约|面试|电话|微信|简历|发一下|看一下|聊一下|什么时候|时间|加.*微信|发.*简历/.test(text)) {
        return {
          actionLevel: 'P0',
          nextStep: '立刻回复'
        };
      }

      return {
        actionLevel: 'P0',
        nextStep: '点进去看对方说了什么'
      };
    }

    // 已读但未回。
    if (status === '已读') {
      return {
        actionLevel: 'P1',
        nextStep: '已读未回，4-6小时后可追问'
      };
    }

    // 送达但未读。
    if (status === '送达') {
      return {
        actionLevel: 'P2',
        nextStep: '未读，先等，不要追'
      };
    }

    return {
      actionLevel: 'P3',
      nextStep: '普通等待'
    };
  }

  function parseRaw(raw) {
    raw = clean(raw);

    const timeMatch = raw.match(/(刚刚|\d{1,2}:\d{2}|昨天|前天|\d{1,2}-\d{1,2}|\d{1,2}月\d{1,2}日)/);
    const time = timeMatch ? timeMatch[1] : '';

    let body = raw;

    // 删除时间，避免“17:26 刘女士...”导致联系人为空。
    if (timeMatch) {
      body = clean(raw.slice(0, timeMatch.index) + ' ' + raw.slice(timeMatch.index + time.length));
    }

    const statusMatch = body.match(/\[(送达|已读|未读)\]/);
    const status = statusMatch ? statusMatch[1] : '对方回复/无状态';

    let headText = '';
    let lastMsg = '';

    if (statusMatch) {
      // 例：张女士示例科技HR [送达] 您好...
      headText = clean(body.slice(0, statusMatch.index));
      lastMsg = clean(body.slice(statusMatch.index));
    } else {
      // 例：刘女士示例科技人力资源HR 感谢您的关注...
      const split = splitByRoleBoundary(body) || fallbackSplitMessage(body);
      headText = split.head;
      lastMsg = split.msg;
    }

    const parsed = parseHeader(headText);

    let suggest = '等对方';

    if (status === '对方回复/无状态') {
      suggest = '优先点进去看';
    }

    if (/很遗憾|不合适|不匹配|不能与您共事|暂时不考虑|不太符合|暂无合适|祝您.*找到/.test(raw)) {
      suggest = '可能拒绝/低优先级';
    }

    if (/方便|可以|约|面试|电话|微信|简历|发一下|看一下|聊一下|什么时候|时间/.test(raw)
      && status === '对方回复/无状态'
      && !/很遗憾|不合适|不匹配|不能与您共事|暂时不考虑|不太符合/.test(raw)) {
      suggest = '高优先级回复';
    }

    const action = buildAction(status, raw);

    return {
      联系人: parsed.name,
      公司: parsed.company,
      身份: parsed.role,
      时间: time,
      状态: status,
      行动等级: action.actionLevel,
      下一步: action.nextStep,
      建议: suggest,
      最后消息: lastMsg,
      原始文本: raw
    };
  }

  function getTextChunks(el) {
    const chunks = [];
    const walker = document.createTreeWalker(
      el,
      NodeFilter.SHOW_TEXT,
      {
        acceptNode(node) {
          const text = clean(node.nodeValue);
          if (!text) return NodeFilter.FILTER_REJECT;
          if (!node.parentElement) return NodeFilter.FILTER_REJECT;
          return NodeFilter.FILTER_ACCEPT;
        }
      }
    );

    let node;
    while ((node = walker.nextNode())) {
      const text = clean(node.nodeValue);
      if (!text) continue;

      const parent = node.parentElement;
      const style = window.getComputedStyle(parent);
      if (style.display === 'none' || style.visibility === 'hidden') continue;

      const range = document.createRange();
      range.selectNodeContents(node);
      const r = range.getBoundingClientRect();

      if (r.width < 1 || r.height < 1) continue;
      if (r.left < 0 || r.top < 0) continue;

      chunks.push({
        text,
        left: r.left,
        top: r.top
      });
    }

    const seen = new Set();

    return chunks
      .sort((a, b) => {
        if (Math.abs(a.top - b.top) > 6) return a.top - b.top;
        return a.left - b.left;
      })
      .filter(x => {
        const key = `${x.text}-${Math.round(x.left)}-${Math.round(x.top)}`;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      })
      .map(x => x.text);
  }

  function getScrollBox() {
    const candidates = Array.from(document.querySelectorAll('div'))
      .map(el => {
        const r = el.getBoundingClientRect();
        return {
          el,
          left: r.left,
          top: r.top,
          width: r.width,
          height: r.height,
          scrollHeight: el.scrollHeight,
          clientHeight: el.clientHeight
        };
      })
      .filter(x =>
        x.left >= 0 &&
        x.left < 700 &&
        x.width > 250 &&
        x.height > 300 &&
        x.scrollHeight > x.clientHeight + 100
      )
      .sort((a, b) =>
        (b.scrollHeight - b.clientHeight) - (a.scrollHeight - a.clientHeight)
      );

    return candidates.length ? candidates[0].el : null;
  }

  function collectVisibleItems() {
    const nodes = Array.from(document.querySelectorAll('div, li, a'));
    const rows = [];
    const seen = new Set();

    for (const el of nodes) {
      const r = el.getBoundingClientRect();
      const style = window.getComputedStyle(el);

      if (style.display === 'none' || style.visibility === 'hidden') continue;

      // 只抓左侧聊天列表，尽量避免抓到右侧聊天正文。
      if (r.left < 0 || r.left > 720) continue;
      if (r.top < 40 || r.bottom > window.innerHeight + 30) continue;

      // 单个聊天卡片的大致范围。
      if (r.width < 250 || r.width > 720) continue;
      if (r.height < 45 || r.height > 160) continue;

      const chunks = getTextChunks(el);
      const text = clean(chunks.length >= 2 ? chunks.join(' ') : el.innerText);

      if (!text) continue;
      if (text.length < 8 || text.length > 420) continue;

      const looksLikeChat =
        /(送达|已读|未读|刚刚|\d{1,2}:\d{2}|昨天|前天|\d{1,2}-\d{1,2}|HR|HRBP|HRM|hrbp|招聘|人事|人力资源|招聘者|招聘人员|CEO|架构师)/.test(text);

      if (!looksLikeChat) continue;

      // 过滤掉包含多个会话的大容器。
      const timeMatches = text.match(/(刚刚|\d{1,2}:\d{2}|昨天|前天|\d{1,2}-\d{1,2})/g) || [];
      if (timeMatches.length >= 3) continue;

      if (seen.has(text)) continue;
      seen.add(text);

      rows.push({
        raw: text,
        top: r.top
      });
    }

    return rows.sort((a, b) => a.top - b.top).map(x => x.raw);
  }

  async function exportBossChat() {
    const scrollBox = getScrollBox();
    const all = [];
    const seen = new Set();

    if (scrollBox) {
      scrollBox.scrollTop = 0;
      await sleep(800);
    }

    for (let i = 0; i < MAX_SCROLLS; i++) {
      const items = collectVisibleItems();

      for (const item of items) {
        if (!seen.has(item)) {
          all.push(item);
          seen.add(item);
        }
      }

      if (scrollBox) {
        scrollBox.scrollBy(0, scrollBox.clientHeight * 0.85);
      } else {
        window.scrollBy(0, window.innerHeight * 0.85);
      }

      await sleep(500);
    }

    const data = all.map(parseRaw);

    const headers = ['联系人', '公司', '身份', '时间', '状态', '行动等级', '下一步', '建议', '最后消息', '原始文本'];

    const tsv = [
      headers.join('\t'),
      ...data.map(row =>
        headers.map(h =>
          String(row[h] || '')
            .replace(/\t/g, ' ')
            .replace(/\n/g, ' ')
        ).join('\t')
      )
    ].join('\n');

    try {
      GM_setClipboard(tsv);
      showResult(tsv, data.length, true);
    } catch (e) {
      showResult(tsv, data.length, false);
    }
  }

  function showResult(text, count, copied) {
    const old = document.getElementById('boss-export-panel');
    if (old) old.remove();

    const panel = document.createElement('div');
    panel.id = 'boss-export-panel';
    panel.style.cssText = `
      position: fixed;
      left: 20px;
      top: 90px;
      width: 620px;
      height: 460px;
      background: #fff;
      z-index: 999999;
      border: 1px solid #ddd;
      box-shadow: 0 4px 20px rgba(0,0,0,.2);
      border-radius: 10px;
      padding: 12px;
      font-size: 14px;
      color: #333;
    `;

    panel.innerHTML = `
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
        <b>聊天状态导出结果 v1.2.0</b>
        <button id="boss-export-close" style="border:none;background:#eee;padding:4px 8px;border-radius:6px;cursor:pointer;">关闭</button>
      </div>
      <div style="margin-bottom:8px;color:#666;">
        已整理 ${count} 条。${copied ? '已自动复制到剪贴板。' : '自动复制失败，请手动复制下面内容。'}
      </div>
      <textarea id="boss-export-textarea" style="width:100%;height:360px;box-sizing:border-box;font-size:12px;"></textarea>
    `;

    document.body.appendChild(panel);

    const textarea = document.getElementById('boss-export-textarea');
    textarea.value = text;
    textarea.focus();
    textarea.select();

    document.getElementById('boss-export-close').onclick = () => panel.remove();
  }

  const JOB_FIT_KEYWORDS = {
    strongDirections: [
      'Java开发实习生', 'Java实习生', 'Java后端', '后端开发', '服务端开发',
      'Java开发实习', '产品研发-Java开发实习', 'Java研发-实习', 'Java研发实习',
      '后端开发实习生', '后端研发实习生',
      'AI应用开发', '大模型应用', 'Agent', '智能体', 'RAG'
    ],
    mediumDirections: ['软件开发实习生', '研发实习生', '全栈工程师'],
    nonTechDirections: ['运营', '销售', '客服', '剪辑', '设计', '编辑', '标注'],
    javaStack: [
      'Java', 'Spring', 'Spring Boot', 'SpringCloud', 'Spring Cloud',
      'MyBatis', 'MyBatis-Plus', 'MyBatis Plus', 'MySQL', 'Redis', 'MQ',
      'RabbitMQ', 'Kafka', 'Nginx', 'JVM', '微服务', '后端接口', '数据库设计', '缓存',
      'SpringMVC', 'Dubbo'
    ],
    aiStack: [
      'AI应用', '大模型', 'LLM', 'Agent', '智能体', 'RAG', 'Prompt',
      'Tool Calling', 'LangChain', 'AutoGen', '知识库', '大模型API',
      '工作流', 'Docker', 'Python', '向量检索', 'AI模型接口',
      '大模型接口', 'Coze', 'Dify', 'Go', 'K8s', 'AI应用后端'
    ],
    workHigh: [
      '接口开发', '后端服务开发', '模块开发', '系统开发', '数据库开发',
      '功能实现', '服务联调', '问题排查', '性能优化', 'AI应用开发',
      'RAG流程开发', 'Agent流程开发', '后端业务模块', '后端开发',
      'Java后端开发', '系统架构设计', '高并发', '高负载', '高可用',
      '微服务', '线上问题', 'Bug修复', '后端研发'
    ],
    workLow: [
      '资料整理', '内容维护', '账号运营', '客户支持', '文档归档',
      '数据标注', '简单配置', '平台操作', '售前方案', 'PPT'
    ],
    companyValue: [
      '上市公司', '100-499人', '500-999人', '1000-9999人',
      '技术团队', '研发中心', '软件研发部', '平台研发',
      '汽车', '金融科技', '企业服务', '人工智能', '软件服务',
      '字节跳动', '京东', '美团', '快手', '百度', '阿里', '腾讯',
      '度小满', '顺丰', '中科曙光', '独角兽', '10000人以上'
    ],
    companyRisk: ['外包', '驻场', '客户现场', '实施交付'],
    risk: [
      '运营', '销售', '客服', '剪辑', '设计', '学科编辑', '纯标注',
      '测试', '实施', '运维', '低代码', '驻场', '外派', '客户现场',
      '6天/周', '12个月', '资料整理', '账号维护', '内容维护',
      '售前', 'PPT', 'ERP', 'OA', '.NET/JAVA', 'JSP', 'Struts'
    ],
    frontend: ['全栈', 'React', 'Vue', 'Node', '前端']
  };

  const JOB_FIT_COLORS = {
    优先投: '#16a34a',
    可投: '#2563eb',
    谨慎投: '#f97316',
    不投: '#6b7280'
  };

  let jobFitLastResult = null;
  let jobFitLastKey = '';
  let jobFitAiResult = null;
  let jobFitAiError = '';
  let jobFitAiLoading = false;
  let jobFitTimer = null;
  let jobFitCollapsed = false;

  function getVisibleJobText() {
    return clean(document.body ? document.body.innerText : '');
  }

  function findJobDetailContainer() {
    const detailHints = ['职位描述', '岗位职责', '任职要求', '工作地址', '立即沟通', '收藏'];
    const fieldHints = [
      /(\d{2,4}-\d{2,4}元\/天|\d{1,3}-\d{1,3}K|\d{1,3}K)/,
      /(经验|在校|应届|不限|1年以内|1-3年|3-5年|5-10年)/,
      /(本科|硕士|大专|学历不限)/,
      /(北京|天津|上海|深圳|广州|杭州|成都|武汉|西安)/,
      /(职位描述|岗位职责|任职要求)/,
      /工作地址/
    ];

    const candidates = Array.from(document.querySelectorAll('main, section, article, div'))
      .map(el => {
        const rect = el.getBoundingClientRect();
        const text = clean(el.innerText);
        if (!text || text.length < 120) return null;
        if (rect.width < 260 || rect.height < 220) return null;
        if (rect.right < window.innerWidth * 0.38) return null;

        const detailScore = detailHints.reduce((sum, hint) => sum + (text.includes(hint) ? 1 : 0), 0);
        const fieldScore = fieldHints.reduce((sum, re) => sum + (re.test(text) ? 1 : 0), 0);
        const salaryCount = (text.match(/(\d{2,4}-\d{2,4}元\/天|\d{1,3}-\d{1,3}K|\d{1,3}K)/g) || []).length;
        const hasDescription = /(职位描述|岗位职责|任职要求)/.test(text);
        const hasAddress = text.includes('工作地址');

        // 左侧列表通常会有多个薪资和岗位标题，但缺少完整描述与地址。
        if (salaryCount >= 4 && !hasDescription && !hasAddress) return null;
        if (detailScore < 2 && fieldScore < 4) return null;

        const score =
          detailScore * 20 +
          fieldScore * 10 +
          (hasDescription ? 25 : 0) +
          (hasAddress ? 20 : 0) +
          Math.min(text.length / 120, 20) +
          (rect.left > window.innerWidth * 0.35 ? 12 : 0);

        return { el, text, score };
      })
      .filter(Boolean)
      .sort((a, b) => b.score - a.score);

    return candidates.length ? candidates[0].el : null;
  }

  function firstMatch(text, patterns) {
    for (const pattern of patterns) {
      const match = text.match(pattern);
      if (match) return clean(match[1] || match[0]);
    }
    return '';
  }

  const JOB_CITY_PATTERN = /(北京|天津|上海|深圳|广州|杭州|成都|武汉|西安|南京|苏州|重庆|长沙|郑州|青岛|厦门|合肥|宁波|佛山|东莞|无锡|济南|大连|沈阳|长春|哈尔滨|福州|南昌|昆明|贵阳|南宁|太原|石家庄|呼和浩特|乌鲁木齐|兰州|银川|西宁|海口)/;

  function parseSalary(text) {
    const normalized = clean(text).replace(/\s+/g, '');
    const day = normalized.match(/(\d{2,4})(?:-(\d{2,4}))?元\/天/);
    if (day) {
      return {
        raw: day[0],
        type: 'daily',
        low: Number(day[1]),
        high: Number(day[2] || day[1])
      };
    }

    const monthly = normalized.match(/(\d{1,3})(?:-(\d{1,3}))?K(?:·\d+薪|\/月)?/);
    if (monthly) {
      return {
        raw: monthly[0],
        type: 'monthly',
        low: Number(monthly[1]),
        high: Number(monthly[2] || monthly[1])
      };
    }

    return { raw: '', type: '', low: 0, high: 0 };
  }

  function isLikelySalaryText(text) {
    const value = clean(text);
    if (!value) return false;
    return /(?:\d{2,4}\s*-\s*\d{2,4}\s*元\s*\/\s*天|\d{1,3}\s*-\s*\d{1,3}\s*K(?:\s*·\s*\d+\s*薪|\s*\/\s*月)?)/i.test(value);
  }

  function getElementOwnText(el) {
    if (!el || !el.childNodes) return '';

    return clean(Array.from(el.childNodes)
      .filter(node => node.nodeType === Node.TEXT_NODE)
      .map(node => node.nodeValue || '')
      .join(' '));
  }

  function isVisibleElement(el) {
    if (!el) return false;
    if (el.nodeType === Node.TEXT_NODE) {
      return isVisibleElement(el.parentElement);
    }
    if (!el.getBoundingClientRect) return true;

    const style = window.getComputedStyle ? window.getComputedStyle(el) : null;
    if (style && (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0)) {
      return false;
    }

    const rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  }

  function findVisibleTextNodes(container) {
    if (!container) return [];

    const result = [];
    const walker = document.createTreeWalker(
        container,
        NodeFilter.SHOW_TEXT,
        {
          acceptNode(node) {
            const text = clean(node.nodeValue);
            if (!text || !isVisibleElement(node.parentElement)) {
              return NodeFilter.FILTER_REJECT;
            }
            return NodeFilter.FILTER_ACCEPT;
          }
        }
    );

    let node = walker.nextNode();
    while (node) {
      result.push({
        node,
        parent: node.parentElement,
        text: clean(node.nodeValue)
      });
      node = walker.nextNode();
    }

    return result;
  }

  function collectVisibleSalaryCandidates() {
    if (!document.body) return [];

    return Array.from(document.body.querySelectorAll('*'))
      .filter(isVisibleElement)
      .map(el => {
        const ownText = getElementOwnText(el);
        const fallbackText = ownText ? '' : clean(el.textContent || '');
        const text = ownText || (fallbackText.length <= 40 ? fallbackText : '');
        if (!isLikelySalaryText(text)) return null;

        return {
          text,
          el,
          rect: el.getBoundingClientRect()
        };
      })
      .filter(Boolean);
  }

  function isSearchConditionText(text) {
    const value = clean(text);
    if (!value) return true;
    if (/搜索职位|搜索公司|推荐|筛选|职位类型|工作地点|薪资待遇|经验要求/.test(value)) return true;
    if (/^(Java|java|AI|后端|前端|测试|产品|运营)[(（]?[^\s()（）]{2,8}[)）]?$/.test(value) && JOB_CITY_PATTERN.test(value)) return true;
    if (/^(Java|java|AI|后端|前端|测试|产品|运营)$/.test(value)) return true;
    return false;
  }

  function cleanJobTitle(title) {
    let value = String(title || '')
      .replace(/^(岗位标题|职位名称|职位)[:：]\s*/, '')
      .replace(/\s*(收藏|立即沟通).*$/, '')
      .trim();

    const salaryMatch = value.match(/\d{2,4}\s*-\s*\d{2,4}\s*元\/天|\d{1,3}\s*-\s*\d{1,3}\s*K(?:·\d+薪|\/月)?/);
    if (salaryMatch && salaryMatch.index > 0) {
      value = value.slice(0, salaryMatch.index).trim();
    }

    const metaMatch = value.match(new RegExp(`\\s+${JOB_CITY_PATTERN.source}\\s+(?:每周\\s*\\d|\\d(?:-\\d)?\\s*天\\/周|\\d+\\s*个月|本科|大专|硕士|博士)`));
    if (metaMatch && metaMatch.index > 0) {
      value = value.slice(0, metaMatch.index).trim();
    }

    return clean(value)
      .replace(/[|｜]+$/, '')
      .trim();
  }

  function isValidJobTitle(title) {
    const value = cleanJobTitle(title);
    if (!value || value.length < 4 || value.length > 80) return false;
    if (isSearchConditionText(value)) return false;
    if (/^\d{2,4}-\d{2,4}元\/天$/.test(value)) return false;
    if (/^\d{1,3}-\d{1,3}K/.test(value)) return false;
    if (/^(北京|天津|上海|深圳|广州|杭州|成都|武汉|西安)\s/.test(value)) return false;
    return /(Java|java|后端|服务端|AI|Agent|RAG|大模型|全栈|软件|研发|开发|工程师|实习|客户端|\.NET|C#|GIS|算法)/.test(value);
  }

  function getElementText(el) {
    return clean(el ? (el.innerText || el.textContent || '') : '');
  }

  function findJobHeaderBlock(container) {
    if (!container) return null;

    const titleSelectors = [
      'h1',
      'h2',
      '[class*="job-name"]',
      '[class*="jobName"]',
      '[class*="position"]',
      '[class*="name"]',
      '[title]'
    ];
    const titleNode = findJobTitleElement(container);

    if (titleNode) {
      let block = titleNode;
      for (let i = 0; i < 4 && block.parentElement && block.parentElement !== container; i += 1) {
        const parentText = getElementText(block.parentElement);
        if (parentText.length > 40 && parentText.length < 1200 && parseSalary(parentText).raw) {
          return block.parentElement;
        }
        block = block.parentElement;
      }
      return titleNode.parentElement || titleNode;
    }

    const blocks = Array.from(container.querySelectorAll('section, article, div'))
      .map(el => ({ el, text: getElementText(el) }))
      .filter(item =>
        item.text.length >= 30 &&
        item.text.length <= 1200 &&
        parseSalary(item.text).raw &&
        /(天\/周|每周|个月|本科|大专|硕士|博士|学历不限)/.test(item.text) &&
        !/(职位描述|岗位职责|任职要求).{200,}/.test(item.text)
      )
      .sort((a, b) => a.text.length - b.text.length);

    return blocks.length ? blocks[0].el : container;
  }

  function findJobTitleElement(container) {
    if (!container) return null;

    const titleSelectors = [
      'h1',
      'h2',
      '[class*="job-name"]',
      '[class*="jobName"]',
      '[class*="position"]',
      '[class*="name"]',
      '[title]'
    ];
    const nodes = Array.from(container.querySelectorAll(titleSelectors.join(',')));

    return nodes.find(node =>
      isValidJobTitle(getElementOwnText(node)) ||
      isValidJobTitle(getElementText(node)) ||
      isValidJobTitle(node.getAttribute('title'))
    ) || null;
  }

  function extractJobTitleFromHeader(container) {
    const header = findJobHeaderBlock(container);
    const sources = [];

    if (header) {
      const nodes = Array.from(header.querySelectorAll('h1,h2,[class*="job-name"],[class*="jobName"],[class*="position"],[class*="name"],[title]'));
      for (const node of nodes) {
        sources.push(getElementText(node) || node.getAttribute('title') || '');
      }
      sources.push(...getElementText(header).split('\n'));
    }

    if (container) {
      sources.push(...getElementText(container).split('\n').slice(0, 20));
    }

    for (const source of sources) {
      const title = cleanJobTitle(source);
      if (isValidJobTitle(title)) {
        return { value: title, source: header ? 'header-title' : 'detail-lines' };
      }
    }

    return { value: '', source: 'unresolved' };
  }

  function extractSalaryFromHeader(container) {
    const header = findJobHeaderBlock(container);
    const titleElement = findJobTitleElement(container);
    const headerTextNodes = findVisibleTextNodes(header);
    const exactHeaderNode = headerTextNodes.find(item => isLikelySalaryText(item.text) && clean(item.text) === parseSalary(item.text).raw);
    if (exactHeaderNode) return { value: parseSalary(exactHeaderNode.text), source: 'header-salary-node' };

    const anyHeaderNode = headerTextNodes.find(item => isLikelySalaryText(item.text));
    if (anyHeaderNode) return { value: parseSalary(anyHeaderNode.text), source: 'header-salary-node' };

    const headerSalaryElement = header ? Array.from(header.querySelectorAll('*'))
      .filter(isVisibleElement)
      .map(el => getElementOwnText(el) || (clean(el.textContent || '').length <= 40 ? clean(el.textContent || '') : ''))
      .find(isLikelySalaryText) : '';
    if (headerSalaryElement) return { value: parseSalary(headerSalaryElement), source: 'header-salary-node' };

    const nearbyTexts = [];
    if (header) {
      nearbyTexts.push(getElementText(header));
      if (header.parentElement) nearbyTexts.push(getElementText(header.parentElement));
    }

    for (const text of nearbyTexts) {
      const salary = parseSalary(text);
      if (salary.raw) return { value: salary, source: 'header-nearby-text' };
    }

    const geometrySalary = extractSalaryByGeometry(container, titleElement);
    if (geometrySalary.value.raw) {
      return geometrySalary;
    }

    const topText = getElementText(container).slice(0, 1000);
    const topSalary = parseSalary(topText);
    if (topSalary.raw) {
      return { value: topSalary, source: 'detail-container-text' };
    }

    return { value: parseSalary(''), source: 'unresolved' };
  }

  function extractSalaryByGeometry(detailContainer, titleElement) {
    if (!detailContainer || !detailContainer.getBoundingClientRect) {
      return { value: parseSalary(''), source: 'unresolved' };
    }

    const detailRect = detailContainer.getBoundingClientRect();
    const titleRect = titleElement && titleElement.getBoundingClientRect ? titleElement.getBoundingClientRect() : null;
    const candidates = collectVisibleSalaryCandidates()
      .filter(candidate =>
        candidate.rect.left >= detailRect.left - 20 &&
        candidate.rect.right <= detailRect.right + 20 &&
        candidate.rect.top >= detailRect.top - 20 &&
        candidate.rect.top <= detailRect.top + 180
      )
      .sort((a, b) => {
        const aDistance = titleRect ? Math.abs(a.rect.top - titleRect.top) : Math.abs(a.rect.top - detailRect.top);
        const bDistance = titleRect ? Math.abs(b.rect.top - titleRect.top) : Math.abs(b.rect.top - detailRect.top);
        if (aDistance !== bDistance) return aDistance - bDistance;
        return a.rect.left - b.rect.left;
      });

    if (!candidates.length) {
      return { value: parseSalary(''), source: 'unresolved' };
    }

    return {
      value: parseSalary(candidates[0].text),
      source: 'geometry-header-nearby'
    };
  }

  function extractCityFromMetaLine(container) {
    const header = findJobHeaderBlock(container);
    const headerLines = getElementText(header).split(/职位描述|岗位职责|任职要求|工作地址|\n/).map(line => clean(line)).filter(Boolean);
    const detailLines = getElementText(container).split(/职位描述|岗位职责|任职要求|\n/).map(line => clean(line)).filter(Boolean);
    const candidateLines = headerLines.concat(detailLines.slice(0, 25));

    for (const line of candidateLines) {
      if (/工作地址|地址|地图/.test(line)) continue;
      if (!/(天\/周|每周|个月|本科|大专|硕士|博士|学历不限|经验不限|在校|应届)/.test(line)) continue;
      const city = firstMatch(line, [JOB_CITY_PATTERN]);
      if (city) return { value: city, source: 'meta-line' };
    }

    for (const line of detailLines) {
      if (!/工作地址|地址/.test(line)) continue;
      const city = firstMatch(line, [JOB_CITY_PATTERN]);
      if (city) return { value: city, source: 'address-fallback' };
    }

    return { value: '', source: 'unresolved' };
  }

  function parseExperience(text) {
    return firstMatch(text, [
      /(经验不限)/,
      /(在校\/应届|在校|应届)/,
      /(\d+\s*年以内)/,
      /(\d+\s*-\s*\d+\s*年)/,
      /(\d+\s*年以上)/
    ]);
  }

  function parseEducation(text) {
    return firstMatch(text, [/(学历不限)/, /(大专)/, /(本科)/, /(硕士)/, /(博士)/]);
  }

  function parseScheduleAndDuration(text) {
    const lines = String(text || '').split('\n').map(line => clean(line)).filter(Boolean);
    const preferredScheduleLine = lines.find(line =>
      /(出勤|每周|天\/周)/.test(line) && /(每周\s*\d(?:-\d)?\s*天|\d(?:-\d)?\s*天\/周|6\s*天\/周)/.test(line)
    );
    const preferredDurationLine = lines.find(line =>
      /(周期|时长|至少|个月|一年|1\s*年)/.test(line) && /(\d+\s*个月以上|至少\s*\d+\s*个月|\d+\s*个月|一年|1\s*年)/.test(line)
    );

    const scheduleSource = preferredScheduleLine || text;
    const durationSource = preferredDurationLine || text;
    const schedule = firstMatch(scheduleSource, [
      /(每周\s*\d(?:-\d)?\s*天)/,
      /(\d(?:-\d)?\s*天\/周)/,
      /(6\s*天\/周)/
    ]);
    const duration = firstMatch(durationSource, [
      /(\d+\s*个月以上)/,
      /(至少\s*\d+\s*个月)/,
      /(\d+\s*个月)/,
      /(一年)/,
      /(1\s*年)/
    ]);

    return { schedule, duration };
  }

  function extractTags(text) {
    const knownTags = []
      .concat(JOB_FIT_KEYWORDS.javaStack)
      .concat(JOB_FIT_KEYWORDS.aiStack)
      .concat(JOB_FIT_KEYWORDS.frontend)
      .concat(['监管报送', '信息披露', '银行理财', 'Oracle', 'vue', 'Vue', '资管']);

    return uniqueMatches(text, knownTags);
  }

  function extractJobInfoFromDetail(container) {
    const sourceType = container ? 'detail-panel' : 'fallback-body';
    const rawText = container ? (container.innerText || '') : getVisibleJobText();
    const text = clean(rawText);
    const lines = rawText.split('\n').map(line => clean(line)).filter(Boolean);
    const titleInfo = extractJobTitleFromHeader(container);
    const salaryExtract = extractSalaryFromHeader(container);
    const cityInfo = extractCityFromMetaLine(container);
    const salaryInfo = salaryExtract.value.raw ? salaryExtract.value : parseSalary(text);
    const scheduleInfo = parseScheduleAndDuration(text);
    const titleLineRaw = lines.find(line =>
      /岗位标题|职位名称/.test(line)
    ) || lines.find(line =>
      line.length <= 50 && isValidJobTitle(line)
    ) || '';
    const titleLine = titleLineRaw.replace(/^(岗位标题|职位名称)[:：]\s*/, '');
    const companyLine = lines.find(line =>
      line.length <= 60 && /(有限公司|公司|科技|信息|智能|网络|软件|数据|集团)/.test(line) && line !== (titleInfo.value || titleLine)
    ) || '';

    return {
      jobTitle: titleInfo.value || cleanJobTitle(titleLine),
      salary: salaryInfo.raw,
      salaryInfo,
      city: cityInfo.value || getCity(text),
      experience: parseExperience(text),
      education: parseEducation(text),
      schedule: scheduleInfo.schedule,
      duration: scheduleInfo.duration,
      companyName: companyLine,
      companySize: firstMatch(text, [/(0-20人|20-99人|100-499人|500-999人|1000-9999人|10000人以上|10000\+人?)/]),
      address: firstMatch(text, [/工作地址\s*([^\n]{2,80})/, /(天津[^\n]{0,50})/, /(北京[^\n]{0,50})/]),
      tags: extractTags(text),
      jdText: text,
      sourceType,
      titleSource: titleInfo.value ? titleInfo.source : (titleLine ? 'detail-lines' : 'unresolved'),
      salarySource: salaryInfo.raw ? salaryExtract.source : 'unresolved',
      citySource: cityInfo.value ? cityInfo.source : 'text-fallback'
    };
  }

  function uniqueMatches(text, keywords) {
    const lower = text.toLowerCase();
    const seen = new Set();

    for (const keyword of keywords) {
      if (lower.includes(keyword.toLowerCase())) {
        seen.add(keyword);
      }
    }

    return Array.from(seen);
  }

  function detectMatchedKeywords(text) {
    return {
      java: uniqueMatches(text, JOB_FIT_KEYWORDS.javaStack),
      ai: uniqueMatches(text, JOB_FIT_KEYWORDS.aiStack),
      backend: uniqueMatches(text, ['后端', '服务端', '接口开发', '后端接口', '系统开发', '模块开发']),
      work: uniqueMatches(text, JOB_FIT_KEYWORDS.workHigh)
    };
  }

  function detectRiskFlags(text) {
    let flags = uniqueMatches(text, JOB_FIT_KEYWORDS.risk);

    if (flags.includes('销售') && !/(销售实习生|销售专员|电话销售|客户销售|销售岗位|销售岗)/.test(text)) {
      flags = flags.filter(flag => flag !== '销售');
    }

    if (flags.includes('销售') && /(销售系统|销售数据|销售平台|公司业务销售)/.test(text)) {
      flags = flags.filter(flag => flag !== '销售');
    }

    if (flags.includes('客服') && !/(客服实习生|客服专员|在线客服|客服岗|客服岗位)/.test(text)) {
      flags = flags.filter(flag => flag !== '客服');
    }

    if (flags.includes('客服') && /(智能客服系统|客服平台|客户服务系统|客户问题|客户需求|客户支持平台)/.test(text)) {
      flags = flags.filter(flag => flag !== '客服');
    }

    if (flags.includes('运营') && !/(运营实习生|产品运营|内容运营|用户运营|新媒体运营|账号运营|运营专员|运营岗)/.test(text)) {
      flags = flags.filter(flag => flag !== '运营');
    }

    if (flags.includes('运营') && /(运营产品|核心运营产品|电商运营系统|运营平台|运营管理系统|运营后台|业务运营系统)/.test(text)) {
      flags = flags.filter(flag => flag !== '运营');
    }

    if (flags.includes('设计') && !/(平面设计|UI设计|视觉设计|设计师|美工设计)/.test(text)) {
      flags = flags.filter(flag => flag !== '设计');
    }

    if (flags.includes('设计') && /(系统设计|架构设计|数据库设计|接口设计|详细设计|概要设计|高可用系统设计|技术方案设计|模块设计|设计模式)/.test(text)) {
      flags = flags.filter(flag => flag !== '设计');
    }

    if (flags.includes('测试') && !/(测试实习生|软件测试|功能测试岗|测试工程师岗位|测试专员)/.test(text)) {
      flags = flags.filter(flag => flag !== '测试');
    }

    if (flags.includes('测试') && /(单元测试|联调测试|测试用例|Bug修复|协助测试工程师|测试和文档编写|保证交付质量)/i.test(text)) {
      flags = flags.filter(flag => flag !== '测试');
    }

    if (flags.includes('ERP') && !/(ERP|OA).{0,20}(实施|客户现场|驻场|二开|管理系统)|(实施|客户现场|驻场|二开).{0,20}(ERP|OA)/.test(text)) {
      flags = flags.filter(flag => flag !== 'ERP');
    }

    if (flags.includes('实施') && !/(实施交付|实施工程师|实施顾问|驻场实施|客户现场|ERP.{0,20}实施|OA.{0,20}实施)/.test(text)) {
      flags = flags.filter(flag => flag !== '实施');
    }

    if (flags.includes('售前') && !/(售前顾问|售前工程师|售前方案|售前支持|方案售前)/.test(text)) {
      flags = flags.filter(flag => flag !== '售前');
    }

    if (flags.includes('售前') && /(技术方案论证|需求分析|和产品\/业务沟通|参与方案设计)/.test(text)) {
      flags = flags.filter(flag => flag !== '售前');
    }

    const hasDevStack = /(AI应用|Java|Spring|SpringBoot|Spring Boot|SpringCloud|MyBatis|MySQL|Redis|Docker|微服务|API开发|后端|接口|模块开发|系统开发)/i.test(text);
    if (hasDevStack) {
      flags = flags.filter(flag => !['资料整理', '内容维护', '文档归档', '客服', '售前'].includes(flag));
    }

    return flags;
  }

  function scoreKeywordGroup(matches, weights, cap) {
    let score = 0;
    for (const match of matches) {
      score += weights[match] || 4;
    }
    return Math.min(score, cap);
  }

  function scoreDirection(text) {
    if (uniqueMatches(text, JOB_FIT_KEYWORDS.strongDirections).length) return 20;
    if (uniqueMatches(text, JOB_FIT_KEYWORDS.mediumDirections).length) return 12;
    return 0;
  }

  function scoreJavaStack(text, matched) {
    const hasJava = matched.java.some(x => /^Java$/i.test(x) || x.includes('Java'));
    const hasSpring = matched.java.some(x => /Spring/i.test(x));
    const hasMySQL = matched.java.some(x => /MySQL/i.test(x));
    const hasRedis = matched.java.some(x => /Redis/i.test(x));
    const hasMyBatis = matched.java.some(x => /MyBatis/i.test(x));

    if (hasJava && hasSpring && hasMySQL && (hasRedis || hasMyBatis)) return 25;

    return scoreKeywordGroup(matched.java, {
      'Java': 5,
      'Spring': 5,
      'Spring Boot': 5,
      'SpringCloud': 4,
      'Spring Cloud': 4,
      'MySQL': 4,
      'Redis': 4,
      'MyBatis': 4,
      'MyBatis-Plus': 4,
      'SpringMVC': 3,
      'Dubbo': 3,
      'RabbitMQ': 4,
      'Kafka': 4,
      'MQ': 4,
      '后端接口': 4,
      '数据库设计': 4,
      '缓存': 3
    }, 25);
  }

  function scoreAiStack(text, matched) {
    const highAi = /(Agent|智能体|RAG|大模型API|大模型接口|AI模型接口|LLM|Coze|Dify)/i.test(text);
    const backendLang = /(后端接口|后端服务|接口开发|Java|Go|Python|Docker|微服务)/i.test(text);

    if (highAi && backendLang) {
      return Math.min(25, 20 + Math.min(matched.ai.length, 5));
    }

    return scoreKeywordGroup(matched.ai, {
      'AI应用': 5,
      '大模型': 5,
      'LLM': 5,
      'Agent': 5,
      '智能体': 5,
      'RAG': 5,
      'Tool Calling': 5,
      '大模型API': 5,
      'AI模型接口': 5,
      '大模型接口': 5,
      'AI应用后端': 5,
      'Go': 4,
      'K8s': 3,
      'Coze': 4,
      'Dify': 4,
      'Python': 4,
      'Docker': 3
    }, 25);
  }

  function scoreWorkContent(text) {
    const high = uniqueMatches(text, JOB_FIT_KEYWORDS.workHigh);
    const low = uniqueMatches(text, JOB_FIT_KEYWORDS.workLow);
    let score = Math.min(high.length * 4, 20);

    if (low.length > high.length) {
      score = Math.min(score, 8);
    }

    return { score, high, low };
  }

  function scoreCompanyValue(text) {
    let score = 0;

    if (text.includes('上市公司')) score += 8;
    if (/(字节跳动|京东|美团|快手|百度|阿里|腾讯|度小满|顺丰|中科曙光)/.test(text)) score += 10;
    if (/(独角兽|10000人以上)/.test(text)) score += 8;
    if (/(100-499人|500-999人|1000-9999人)/.test(text)) score += 5;
    if (/(技术团队|研发中心|软件研发部|平台研发)/.test(text)) score += 5;
    if (/(汽车|金融科技|企业服务|人工智能|软件服务)/.test(text)) score += 3;
    if (/(外包|驻场|客户现场|实施交付)/.test(text)) score -= 5;

    return Math.max(0, Math.min(score, 15));
  }

  function getJavaInternFloor(text, jobInfo) {
    const titleText = `${jobInfo.jobTitle || ''} ${text}`;
    const isJavaIntern = /(Java实习生|java实习生|Java开发实习|java开发实习|Java开发实习生|JAVA开发实习生|Java研发-实习|Java研发实习|产品研发-Java开发实习|实习-Java开发|后端开发实习生|后端研发实习生)/.test(titleText);
    const hasInternSignal = /(实习|实习生|见习|校招|应届|在校)/.test(titleText);
    const isDailyPay = jobInfo.salaryInfo && jobInfo.salaryInfo.type === 'daily';
    const hardMismatch = /(3\s*-\s*5\s*年|2\s*-\s*5\s*年|5\s*-\s*10\s*年|实际软件开发经验)/.test(text)
      || (jobInfo.salaryInfo && jobInfo.salaryInfo.type === 'monthly');

    if (!isJavaIntern || !hasInternSignal || !isDailyPay || hardMismatch) {
      return { floor: 0, label: '无' };
    }

    const hasJava = /Java/i.test(text);
    const hasSpring = /(SpringBoot|Spring Boot|SpringCloud|Spring Cloud|SpringMVC|Spring)/i.test(text);
    const hasMySQL = /MySQL/i.test(text);
    const hasRedisOrMyBatis = /(Redis|MyBatis)/i.test(text);

    if (!(hasJava && hasSpring && hasMySQL && hasRedisOrMyBatis)) {
      const partialBackendHits = uniqueMatches(text, [
        'MyBatis', 'Docker', '高并发', '数据库设计', '后端研发',
        '后端开发', '系统架构设计', '微服务', '后端业务模块'
      ]);

      if (hasJava && hasSpring && partialBackendHits.length >= 2) {
        return { floor: 68, label: 'Java后端实习保底68' };
      }

      return { floor: 0, label: '无' };
    }

    const advancedHits = uniqueMatches(text, [
      'SpringCloud', 'Spring Boot', 'SpringBoot', 'MyBatis', 'Redis',
      'MQ', 'RabbitMQ', 'Kafka', 'Dubbo', 'SpringMVC', 'Docker',
      '高并发', '高负载', '高可用', '微服务', '后端业务模块',
      'Java后端开发', '线上问题', 'Bug修复'
    ]);

    const salaryHigh = jobInfo.salaryInfo.high >= 250 && (jobInfo.city || '').includes('北京');

    if (salaryHigh && advancedHits.length >= 2) {
      return { floor: 78, label: 'Java后端实习保底75 + 北京高薪' };
    }

    if (advancedHits.length >= 2) {
      return { floor: 75, label: 'Java后端实习保底75' };
    }

    return { floor: 68, label: 'Java后端实习保底68' };
  }

  function getAiBackendFloor(text, jobInfo, direction) {
    const aiTitleSignal = /(AI\s*应用开发后端实习生|AI应用开发后端实习生|AI\s*应用开发|AI应用开发|大模型应用开发|Agent开发|智能体开发|RAG|Coze|Dify|AI模型接口|大模型接口|AI应用后端)/i.test(text);
    const backendSignals = uniqueMatches(text, ['Java', 'Go', 'Python', '后端服务', '接口开发', '后端接口', 'Docker', '微服务', 'K8s']);
    const hasInternSignal = /(实习|实习生|见习|校招|应届|在校)/.test(text);
    const isDailyPay = jobInfo.salaryInfo && jobInfo.salaryInfo.type === 'daily';

    if (!aiTitleSignal || backendSignals.length < 2 || !hasInternSignal || !isDailyPay) {
      return { floor: 0, label: '无' };
    }

    if (direction === 'Java后端 + AI应用') {
      return { floor: 76, label: 'AI应用后端实习保底76' };
    }

    return { floor: 72, label: 'AI应用后端实习保底72' };
  }

  function getScheduleDurationRisk(jobInfo) {
    const scheduleText = jobInfo.schedule || '';
    const durationText = jobInfo.duration || '';
    const hasSevenDays = /(7\s*天\/周|每周\s*7\s*天|6\s*-\s*7\s*天\/周)/.test(scheduleText);
    const hasSixDays = /(6\s*天\/周|每周\s*6\s*天|6\s*-\s*7\s*天\/周)/.test(scheduleText);
    const hasHighIntensitySchedule = hasSixDays || hasSevenDays;
    const hasTwelveMonths = /(12\s*个月|一年|1\s*年)/.test(durationText);
    const hasSixMonths = /(6\s*个月|半年)/.test(durationText);

    return {
      scheduleText,
      durationText,
      hasSixDays,
      hasSevenDays,
      hasHighIntensitySchedule,
      hasTwelveMonths,
      hasSixMonths,
      longInternRisk: hasHighIntensitySchedule || hasTwelveMonths,
      source: hasHighIntensitySchedule ? 'schedule' : (hasTwelveMonths ? 'duration' : 'none'),
      hardRule: hasHighIntensitySchedule ? '高强度出勤' : (hasTwelveMonths ? '12个月' : '无')
    };
  }

  function scoreInternCondition(jobInfo) {
    const scheduleText = jobInfo.schedule || '';
    const durationText = jobInfo.duration || '';

    if (/(每周\s*)?4-5\s*天/.test(scheduleText) && /3\s*个月以上|至少\s*3\s*个月|3\s*个月/.test(durationText)) return 10;
    if (/3\s*天\/周|每周\s*3\s*天/.test(scheduleText) && /3\s*个月以上|至少\s*3\s*个月|3\s*个月/.test(durationText)) return 7;
    if (/5\s*天\/周|每周\s*5\s*天/.test(scheduleText) && /3\s*个月以上|至少\s*3\s*个月|3\s*个月/.test(durationText)) return 10;
    if (/5\s*天\/周|每周\s*5\s*天/.test(scheduleText) && /6\s*个月/.test(durationText)) return 7;
    if (/(6\s*天\/周|7\s*天\/周|每周\s*6\s*天|每周\s*7\s*天|6\s*-\s*7\s*天\/周)/.test(scheduleText)) return 2;
    if (/12\s*个月|一年|1\s*年/.test(durationText)) return 2;
    return 0;
  }

  function scoreSalary(text) {
    const salaryInfo = typeof text === 'object' ? text : parseSalary(text);
    if (!salaryInfo.raw || salaryInfo.type !== 'daily') return 0;

    const pay = Math.max(salaryInfo.low, salaryInfo.high);
    const sourceText = typeof text === 'object' ? (text.city || '') : text;
    const isTianjin = sourceText.includes('天津');
    const isBeijing = sourceText.includes('北京');

    if (isTianjin) {
      if (pay > 200) return 10;
      if (pay >= 150) return 8;
      if (pay >= 100) return 5;
      return 1;
    }

    if (isBeijing) {
      if (pay > 250) return 10;
      if (pay >= 180) return 8;
      if (pay >= 120) return 5;
      return 1;
    }

    if (pay >= 200) return 8;
    if (pay >= 120) return 5;
    return 1;
  }

  function detectDirection(text) {
    const hasCompleteJavaBackendEarly = /Java/i.test(text) && /Spring\s*Boot|SpringBoot|SpringCloud|Spring Cloud|SpringMVC/i.test(text)
      && /MySQL/i.test(text) && /(Redis|MyBatis)/i.test(text);

    if (/(C#|\.NET|SQLServer|Windows|PB)/.test(text) && !hasCompleteJavaBackendEarly) {
      return '.NET/C#/SQLServer非主线';
    }

    if (/(3\s*-\s*5\s*年|2\s*-\s*5\s*年|5\s*-\s*10\s*年|社招)/.test(text) || /\d{1,3}(?:-\d{1,3})?K/.test(text)) {
      return '社招不匹配';
    }

    const matched = detectMatchedKeywords(text);
    const javaScore = scoreJavaStack(text, matched);
    const aiScore = scoreAiStack(text, matched);
    const frontendScore = uniqueMatches(text, JOB_FIT_KEYWORDS.frontend).length;
    const nonTechScore = uniqueMatches(text, JOB_FIT_KEYWORDS.nonTechDirections).length;
    const hasCompleteJavaBackend = /Java/i.test(text) && /Spring\s*Boot|SpringBoot|SpringCloud|Spring Cloud|SpringMVC/i.test(text)
      && /MySQL/i.test(text) && /(Redis|MyBatis)/i.test(text);
    const backendSignalCount = uniqueMatches(text, ['后端开发', 'Java后端开发', '后端业务模块', '接口开发', '微服务', '高并发', '高负载', '高可用']).length;
    const aiTitleSignal = /(AI\s*应用开发后端实习生|AI应用开发后端实习生|AI\s*应用开发|AI应用开发|大模型应用开发|Agent开发|智能体开发|RAG|Coze|Dify|AI模型接口|大模型接口|AI应用后端)/i.test(text);
    const aiBackendSignals = uniqueMatches(text, ['Java', 'Go', 'Python', '后端服务', '接口开发', '后端接口', 'Docker', '微服务']);
    const clientSignalCount = uniqueMatches(text, ['客户端', '移动端', 'Android', 'iOS', 'OC', 'Swift', '抖音客户端', '移动产品']).length;
    const gisSignalCount = uniqueMatches(text, ['遥感', 'GIS', 'ArcGIS', 'ENVI', 'ERDAS', 'SPOT', 'WorldView', '图像处理', '航空航天']).length;
    const dotnetSignalCount = uniqueMatches(text, ['C#', '.NET', 'SQLServer', 'Windows', 'PB']).length;

    if (aiTitleSignal && aiBackendSignals.length >= 2) {
      return /(Java|Go)/i.test(text) ? 'Java后端 + AI应用' : 'AI应用后端 / Agent应用';
    }
    if (hasCompleteJavaBackend) return aiScore >= 18 ? 'Java后端 + AI应用' : 'Java后端';
    if (clientSignalCount >= 2 && backendSignalCount < 2) return '客户端/非Java后端主线';
    if (gisSignalCount >= 2 && backendSignalCount < 2) return '遥感/GIS/图像处理';
    if (dotnetSignalCount >= 2 && javaScore < 18) return '.NET/C#/SQLServer非主线';
    if (nonTechScore >= 2 && javaScore < 8 && aiScore < 8) return '低匹配/非技术';
    if (javaScore >= 18 && aiScore >= 18) return 'Java后端 + AI应用';
    if (aiScore >= 18) return 'AI应用后端 / Agent应用';
    if (javaScore >= 18) return 'Java后端';
    if (frontendScore >= 2 || /软件开发|研发实习|全栈/.test(text)) return '全栈/软件开发';
    return '低匹配/非技术';
  }

  function getConclusion(score, riskFlags, context) {
    let conclusion = score >= 80 ? '优先投' : score >= 65 ? '可投' : score >= 50 ? '谨慎投' : '不投';

    if (context.highIntensitySevenDays) {
      return { conclusion: '不投', excelTier: '暂不投' };
    }

    if (context.nonTechWithoutDev || context.socialRecruitMismatch) {
      conclusion = '不投';
    }

    if (context.deliveryRisk || context.longInternRisk) {
      if (conclusion === '优先投' || conclusion === '可投') {
        conclusion = '谨慎投';
      }
      if (conclusion === '不投' && score >= 40) {
        conclusion = '谨慎投';
      }
    }

    if (riskFlags.includes('测试') && !context.hasStrongDev) {
      conclusion = score >= 45 ? '谨慎投' : '不投';
    }

    const excelTier = {
      优先投: 'A档-高匹配',
      可投: 'B档-可投',
      谨慎投: 'C档-练手',
      不投: '暂不投'
    }[conclusion];

    return { conclusion, excelTier };
  }

  function getGreetingType(direction, city, conclusion) {
    if (conclusion === '不投') return '谨慎不发';
    if (city === '天津') return '天津本地版';
    if (direction.includes('AI') || direction.includes('Agent')) return 'AI应用版';
    if (direction.includes('Java')) return '普通Java版';
    return conclusion === '谨慎投' ? '谨慎不发' : '普通Java版';
  }

  function getCity(text) {
    return firstMatch(text, [JOB_CITY_PATTERN]);
  }

  function extractCompanyPosition(text) {
    const lines = text
      .split('\n')
      .map(line => clean(line))
      .filter(line => line.length >= 2 && line.length <= 50);

    const position = lines.find(line => /(Java|后端|服务端|AI|Agent|RAG|大模型|全栈|软件开发|研发实习)/i.test(line)) || '';
    const company = lines.find(line => /(科技|信息|智能|网络|软件|数据|集团|有限公司|公司)/.test(line) && line !== position) || '';

    return { company, position };
  }

  function normalizeJobInfo(input) {
    if (typeof input === 'string') {
      return extractJobInfoFromDetail(null);
    }

    const info = input || {};
    const text = clean([
      info.jobTitle,
      info.salary,
      info.city,
      info.experience,
      info.education,
      info.schedule,
      info.duration,
      info.companyName,
      info.companySize,
      info.address,
      (info.tags || []).join(' '),
      info.jdText
    ].filter(Boolean).join('\n'));

    return Object.assign({}, info, {
      salaryInfo: info.salaryInfo || parseSalary(info.salary || text),
      jdText: text,
      sourceType: info.sourceType || 'detail-panel'
    });
  }

  function buildReason(result) {
    const parts = [];

    if (result.matchedKeywords.java.length) {
      parts.push(`Java/后端关键词较明确：${result.matchedKeywords.java.slice(0, 4).join('、')}。`);
    }
    if (result.matchedKeywords.ai.length) {
      parts.push(`AI应用相关关键词较明确：${result.matchedKeywords.ai.slice(0, 4).join('、')}。`);
    }
    if (result.riskFlags.length) {
      parts.push(`需要注意风险点：${result.riskFlags.slice(0, 5).join('、')}。`);
    }
    if (!parts.length) {
      parts.push('当前页面可见文本中技术匹配信息较少，建议人工查看岗位详情后再决定。');
    }

    if (result.conclusion === '优先投') parts.unshift('整体匹配度较高，适合优先跟进。');
    if (result.conclusion === '可投') parts.unshift('整体匹配度尚可，可以加入跟进列表。');
    if (result.conclusion === '谨慎投') parts.unshift('存在一定不确定性，建议谨慎判断。');
    if (result.conclusion === '不投') parts.unshift('当前匹配度较低，建议暂不投入过多精力。');

    return parts.slice(0, 3).join('');
  }

  function scoreJob(jobInfo) {
    if (typeof jobInfo === 'string') {
      jobInfo = {
        jdText: clean(jobInfo),
        salaryInfo: parseSalary(jobInfo),
        salary: parseSalary(jobInfo).raw,
        city: getCity(jobInfo),
        experience: parseExperience(jobInfo),
        education: parseEducation(jobInfo),
        tags: extractTags(jobInfo),
        sourceType: 'fallback-body'
      };
    }

    jobInfo = normalizeJobInfo(jobInfo);
    const text = jobInfo.jdText;

    const matchedKeywords = detectMatchedKeywords(text);
    const riskFlags = detectRiskFlags(text);
    const scheduleRisk = getScheduleDurationRisk(jobInfo);
    if (!scheduleRisk.hasSixDays) {
      const idx = riskFlags.indexOf('6天/周');
      if (idx >= 0) riskFlags.splice(idx, 1);
    }
    if (scheduleRisk.hasSixDays && !riskFlags.includes('6天/周')) {
      riskFlags.push('6天/周');
    }
    if (scheduleRisk.hasSevenDays && !riskFlags.includes('7天/周')) {
      riskFlags.push('7天/周');
    }
    if (!scheduleRisk.hasTwelveMonths) {
      const idx = riskFlags.indexOf('12个月');
      if (idx >= 0) riskFlags.splice(idx, 1);
    }
    if (/(3\s*-\s*5\s*年|2\s*-\s*5\s*年|5\s*-\s*10\s*年)/.test(text)) riskFlags.push('社招经验要求');
    if (jobInfo.salaryInfo && jobInfo.salaryInfo.type === 'monthly') riskFlags.push('月薪社招');

    const directionScore = scoreDirection(text);
    const javaScore = scoreJavaStack(text, matchedKeywords);
    const aiScore = scoreAiStack(text, matchedKeywords);
    const work = scoreWorkContent(text);
    const companyScore = scoreCompanyValue(text);
    const internScore = scoreInternCondition(jobInfo);
    const salaryScore = scoreSalary(Object.assign({}, jobInfo.salaryInfo, { city: jobInfo.city }));
    const rawScore = directionScore + javaScore + aiScore + work.score + companyScore + internScore + salaryScore;
    const devKeywordCount = matchedKeywords.java.length + matchedKeywords.ai.length + matchedKeywords.backend.length + work.high.length;
    const fullstackTechFit = /全栈|软件开发|研发实习/.test(text) && devKeywordCount >= 3 && work.high.length >= 1;
    const nonTechHits = uniqueMatches(text, JOB_FIT_KEYWORDS.nonTechDirections);
    const direction = detectDirection(text);
    const city = jobInfo.city || getCity(text);
    const javaInternFloor = getJavaInternFloor(text, jobInfo);
    const aiBackendFloor = getAiBackendFloor(text, jobInfo, direction);
    const nonMainlineDirection = ['客户端/非Java后端主线', '遥感/GIS/图像处理', '.NET/C#/SQLServer非主线'].includes(direction);
    const scoreFloor = Math.max(fullstackTechFit ? 50 : 0, javaInternFloor.floor, aiBackendFloor.floor);
    let score = Math.max(scoreFloor, Math.min(100, rawScore));
    let hardRule = '无';

    if (scheduleRisk.hasSixMonths && !riskFlags.includes('6个月')) {
      riskFlags.push('6个月');
    }

    if (scheduleRisk.hasSixMonths && jobInfo.salaryInfo && jobInfo.salaryInfo.type === 'daily' && jobInfo.salaryInfo.high < 250) {
      score = Math.min(score, 72);
    }

    if (nonMainlineDirection) {
      score = Math.min(score, direction === '客户端/非Java后端主线' ? 62 : 45);
      hardRule = '非主线方向';
    }

    const context = {
      nonTechWithoutDev: nonTechHits.length > 0 && devKeywordCount < 2,
      deliveryRisk: /(实施交付|实施工程师|实施顾问|驻场实施|运维|驻场|外派|客户现场)/.test(text),
      longInternRisk: scheduleRisk.longInternRisk,
      highIntensitySevenDays: scheduleRisk.hasSevenDays,
      javaOnlyWeak: /Java/i.test(text) && !/(Spring|MySQL|Redis|接口开发|后端接口)/i.test(text),
      aiOpsOnly: /(AI工具使用|内容处理|知识库维护|文档整理)/.test(text) && !/(Agent|RAG|大模型API|后端接口|Python|Java)/i.test(text),
      hasStrongDev: devKeywordCount >= 3 || work.high.length >= 2,
      socialRecruitMismatch: /(3\s*-\s*5\s*年|2\s*-\s*5\s*年|5\s*-\s*10\s*年|2\s*-\s*5年实际软件开发经验|实际软件开发经验)/.test(text)
        || (jobInfo.salaryInfo && jobInfo.salaryInfo.type === 'monthly')
    };

    if (context.socialRecruitMismatch) hardRule = '社招不匹配';
    if (context.longInternRisk) hardRule = scheduleRisk.hardRule;

    const conclusionInfo = getConclusion(score, riskFlags, context);

    const result = {
      score,
      rawScore: Math.min(100, rawScore),
      finalScore: score,
      conclusion: conclusionInfo.conclusion,
      excelTier: conclusionInfo.excelTier,
      direction,
      matchedKeywords,
      riskFlags,
      greetingType: getGreetingType(direction, city, conclusionInfo.conclusion),
      companyPosition: {
        company: jobInfo.companyName || extractCompanyPosition(text).company,
        position: jobInfo.jobTitle || extractCompanyPosition(text).position
      },
      city,
      jobInfo,
      sourceType: jobInfo.sourceType,
      ruleInfo: {
        floorRule: javaInternFloor.label !== '无' ? javaInternFloor.label : (aiBackendFloor.label !== '无' ? aiBackendFloor.label : (fullstackTechFit ? '全栈/软件开发保底50' : '无')),
        companyValue: companyScore > 0 ? '公司平台加分' : '无',
        hardRule,
        scheduleRaw: scheduleRisk.scheduleText || '未识别',
        durationRaw: scheduleRisk.durationText || '未识别',
        longInternRiskSource: scheduleRisk.source
      }
    };

    result.reason = buildReason(result);
    return result;
  }

  function escapeHtml(s) {
    return String(s || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function flattenKeywordGroups(groups) {
    return []
      .concat(groups.java || [])
      .concat(groups.backend || [])
      .concat(groups.ai || [])
      .concat(groups.work || [])
      .filter((item, index, arr) => arr.indexOf(item) === index);
  }

  function buildJobAnalysisText(result) {
    const keywords = flattenKeywordGroups(result.matchedKeywords);

    return [
      `公司/岗位：${result.companyPosition.company || ''} ${result.companyPosition.position || ''}`.trim(),
      `结论：${result.conclusion}`,
      `评分：${result.score}`,
      `方向：${result.direction}`,
      `Excel档位：${result.excelTier}`,
      `匹配关键词：${keywords.join('、') || '无'}`,
      `风险点：${result.riskFlags.join('、') || '无'}`,
      `推荐开场白版本：${result.greetingType}`,
      `简短理由：${result.reason}`
    ].join('\n');
  }

  function getJobFitResultKey(result) {
    return [
      result.companyPosition.company,
      result.companyPosition.position,
      result.jobInfo.salary,
      result.city,
      result.jobInfo.schedule,
      result.jobInfo.duration
    ].filter(Boolean).join('|');
  }

  function buildAiAnalyzePayload(jobInfo, scoreResult) {
    return {
      jobTitle: scoreResult.companyPosition.position || jobInfo.jobTitle || '',
      companyName: scoreResult.companyPosition.company || jobInfo.companyName || '',
      salary: jobInfo.salary || '',
      city: scoreResult.city || jobInfo.city || '',
      schedule: jobInfo.schedule || '',
      duration: jobInfo.duration || '',
      jobText: jobInfo.jdText || '',
      ruleScore: scoreResult.finalScore,
      ruleConclusion: scoreResult.conclusion
    };
  }

  function callAiAnalyzeBackend(payload) {
    return new Promise((resolve, reject) => {
      if (typeof GM_xmlhttpRequest !== 'function') {
        reject(new Error('GM_xmlhttpRequest unavailable'));
        return;
      }

      GM_xmlhttpRequest({
        method: 'POST',
        url: 'http://localhost:8080/api/job/analyze',
        headers: {
          'Content-Type': 'application/json'
        },
        data: JSON.stringify(payload),
        timeout: 15000,
        onload: response => {
          if (response.status < 200 || response.status >= 300) {
            reject(new Error(`HTTP ${response.status}`));
            return;
          }

          try {
            resolve(JSON.parse(response.responseText || '{}'));
          } catch (e) {
            reject(new Error('Invalid JSON response'));
          }
        },
        onerror: () => reject(new Error('Request failed')),
        ontimeout: () => reject(new Error('Request timeout'))
      });
    });
  }

  function renderCompactList(items, emptyText) {
    const list = Array.isArray(items) ? items.filter(Boolean) : [];
    if (!list.length) return `<div style="color:#6b7280;">${escapeHtml(emptyText)}</div>`;

    return `<ul style="margin:4px 0 0 18px;padding:0;">${list.slice(0, 4).map(item =>
      `<li style="margin-bottom:3px;">${escapeHtml(item)}</li>`
    ).join('')}</ul>`;
  }

  function renderAiAnalyzeResult(result, error, loading) {
    if (loading) {
      return `
        <div style="margin-top:8px;padding:8px;border-radius:8px;background:#f9fafb;border:1px solid #e5e7eb;color:#6b7280;">
          正在调用本地后端进行 AI 深度核验...
        </div>
      `;
    }

    if (error) {
      return `
        <div style="margin-top:8px;padding:8px;border-radius:8px;background:#fff7ed;border:1px solid #fed7aa;color:#c2410c;">
          ${escapeHtml(error)}
        </div>
      `;
    }

    if (!result) {
      return `
        <div style="margin-top:8px;color:#6b7280;font-size:12px;">
          AI 深度核验仅在手动点击按钮后调用本地后端。
        </div>
      `;
    }

    return `
      <details open style="margin-top:8px;padding:8px;border-radius:8px;background:#f9fafb;border:1px solid #e5e7eb;">
        <summary style="cursor:pointer;font-weight:700;">AI 深度核验结果</summary>
        <div style="margin-top:8px;">
          <div style="margin-bottom:4px;"><b>AI 决策：</b>${escapeHtml(result.decision || '未返回')}</div>
          <div style="margin-bottom:4px;"><b>AI 分数：</b>${escapeHtml(result.score == null ? '未返回' : result.score)}</div>
          <div style="margin-bottom:4px;"><b>方向：</b>${escapeHtml(result.direction || '未返回')}</div>
          <div style="margin:8px 0 4px;"><b>Reasons</b></div>
          ${renderCompactList(result.reasons, '暂无')}
          <div style="margin:8px 0 4px;"><b>Risks</b></div>
          ${renderCompactList(result.risks, '暂无')}
          <div style="margin:8px 0 4px;"><b>Resume Matches</b></div>
          ${renderCompactList(result.resumeMatches, '暂无')}
          <div style="margin:8px 0 4px;"><b>Interview Focus</b></div>
          ${renderCompactList(result.interviewFocus, '暂无')}
          <div style="margin:8px 0 4px;"><b>Suggested Message</b></div>
          <div style="color:#374151;">${escapeHtml(result.suggestedMessage || '暂无')}</div>
        </div>
      </details>
    `;
  }

  function copyText(text) {
    try {
      GM_setClipboard(text);
      return true;
    } catch (e) {
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.left = '-9999px';
      document.body.appendChild(textarea);
      textarea.select();
      const ok = document.execCommand('copy');
      textarea.remove();
      return ok;
    }
  }

  function renderTagList(items, emptyText) {
    if (!items.length) return `<span style="color:#6b7280;">${escapeHtml(emptyText)}</span>`;
    return items.slice(0, 10).map(item =>
      `<span style="display:inline-block;margin:2px 4px 2px 0;padding:2px 6px;border-radius:999px;background:#f3f4f6;color:#374151;">${escapeHtml(item)}</span>`
    ).join('');
  }

  function renderJobFitPanel(result) {
    const resultKey = getJobFitResultKey(result);
    if (resultKey !== jobFitLastKey) {
      jobFitAiResult = null;
      jobFitAiError = '';
      jobFitAiLoading = false;
      jobFitLastKey = resultKey;
    }
    jobFitLastResult = result;

    let panel = document.getElementById('job-fit-scoring-panel');
    const color = JOB_FIT_COLORS[result.conclusion] || '#6b7280';
    const keywords = flattenKeywordGroups(result.matchedKeywords);
    const sourceMessage = result.sourceType === 'detail-panel'
      ? '已识别右侧岗位详情'
      : '未能精准定位右侧详情，结果可能受左侧列表干扰';
    const sourceColor = result.sourceType === 'detail-panel' ? '#16a34a' : '#f97316';

    if (!panel) {
      panel = document.createElement('div');
      panel.id = 'job-fit-scoring-panel';
      panel.style.cssText = `
        position: fixed;
        right: 20px;
        bottom: 24px;
        width: 320px;
        max-height: 72vh;
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

    panel.innerHTML = `
      <div style="display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border-bottom:1px solid #e5e7eb;">
        <div style="font-weight:700;">岗位匹配度 v1.3.1</div>
        <button id="job-fit-toggle" style="border:none;background:#f3f4f6;border-radius:6px;padding:3px 8px;cursor:pointer;">${jobFitCollapsed ? '展开' : '折叠'}</button>
      </div>
      <div id="job-fit-body" style="display:${jobFitCollapsed ? 'none' : 'block'};padding:12px;">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;">
          <span style="font-size:18px;font-weight:700;color:${color};">${escapeHtml(result.conclusion)}</span>
          <span style="font-size:22px;font-weight:800;color:${color};">${result.score}</span>
        </div>
        <div style="margin-bottom:8px;padding:6px 8px;border-radius:8px;background:#f9fafb;color:${sourceColor};font-size:12px;">
          ${escapeHtml(sourceMessage)}
        </div>
        <div style="margin-bottom:6px;"><b>当前识别岗位：</b>${escapeHtml(result.companyPosition.position || '未识别')}</div>
        <div style="margin-bottom:6px;"><b>当前识别薪资：</b>${escapeHtml(result.jobInfo.salary || '未识别')}</div>
        <div style="margin-bottom:6px;"><b>当前识别城市：</b>${escapeHtml(result.city || '未识别')}</div>
        <div style="margin-bottom:6px;"><b>当前识别出勤周期：</b>${escapeHtml([result.jobInfo.schedule, result.jobInfo.duration].filter(Boolean).join(' / ') || '未识别')}</div>
        <div style="margin-bottom:6px;"><b>原始分数：</b>${escapeHtml(result.rawScore)}</div>
        <div style="margin-bottom:6px;"><b>最终分数：</b>${escapeHtml(result.finalScore)}</div>
        <div style="margin-bottom:6px;"><b>保底规则：</b>${escapeHtml(result.ruleInfo.floorRule)}${result.ruleInfo.companyValue !== '无' ? ` / ${escapeHtml(result.ruleInfo.companyValue)}` : ''}</div>
        <div style="margin-bottom:6px;"><b>硬性规则：</b>${escapeHtml(result.ruleInfo.hardRule)}</div>
        <div style="margin:8px 0 4px;color:#6b7280;font-size:12px;"><b>scheduleRaw:</b> ${escapeHtml(result.ruleInfo.scheduleRaw)}</div>
        <div style="margin-bottom:4px;color:#6b7280;font-size:12px;"><b>durationRaw:</b> ${escapeHtml(result.ruleInfo.durationRaw)}</div>
        <div style="margin-bottom:6px;color:#6b7280;font-size:12px;"><b>longInternRiskSource:</b> ${escapeHtml(result.ruleInfo.longInternRiskSource)}</div>
        <div style="margin-bottom:4px;color:#6b7280;font-size:12px;"><b>titleSource:</b> ${escapeHtml(result.jobInfo.titleSource || 'unresolved')}</div>
        <div style="margin-bottom:4px;color:#6b7280;font-size:12px;"><b>salarySource:</b> ${escapeHtml(result.jobInfo.salarySource || 'unresolved')}</div>
        <div style="margin-bottom:6px;color:#6b7280;font-size:12px;"><b>citySource:</b> ${escapeHtml(result.jobInfo.citySource || 'unresolved')}</div>
        <div style="margin-bottom:6px;"><b>方向判断：</b>${escapeHtml(result.direction)}</div>
        <div style="margin-bottom:6px;"><b>Excel 档位：</b>${escapeHtml(result.excelTier)}</div>
        <div style="margin-bottom:6px;"><b>推荐开场白：</b>${escapeHtml(result.greetingType)}</div>
        <div style="margin:8px 0 4px;"><b>命中关键词：</b></div>
        <div>${renderTagList(keywords, '暂无明显技术关键词')}</div>
        <div style="margin:8px 0 4px;"><b>风险点：</b></div>
        <div>${renderTagList(result.riskFlags, '暂无明显风险点')}</div>
        <div style="margin:10px 0 8px;"><b>简短理由：</b>${escapeHtml(result.reason)}</div>
        <button id="job-fit-copy" style="width:100%;border:none;background:${color};color:#fff;border-radius:8px;padding:8px 10px;cursor:pointer;font-weight:700;">复制岗位分析</button>
        <button id="job-fit-ai-analyze" ${jobFitAiLoading ? 'disabled' : ''} style="width:100%;margin-top:8px;border:none;background:#111827;color:#fff;border-radius:8px;padding:8px 10px;cursor:${jobFitAiLoading ? 'not-allowed' : 'pointer'};font-weight:700;opacity:${jobFitAiLoading ? '.65' : '1'};">${jobFitAiLoading ? '分析中...' : 'AI 深度核验'}</button>
        <div id="job-fit-copy-tip" style="margin-top:6px;color:#6b7280;font-size:12px;"></div>
        <div id="job-fit-ai-result">${renderAiAnalyzeResult(jobFitAiResult, jobFitAiError, jobFitAiLoading)}</div>
        <div style="margin-top:10px;color:#6b7280;font-size:12px;border-top:1px solid #e5e7eb;padding-top:8px;">
          仅分析当前页面可见文本，不自动投递，不自动发消息
        </div>
      </div>
    `;

    document.getElementById('job-fit-toggle').onclick = () => {
      jobFitCollapsed = !jobFitCollapsed;
      renderJobFitPanel(jobFitLastResult);
    };

    const copyBtn = document.getElementById('job-fit-copy');
    if (copyBtn) {
      copyBtn.onclick = () => {
        const ok = copyText(buildJobAnalysisText(jobFitLastResult));
        const tip = document.getElementById('job-fit-copy-tip');
        if (tip) tip.textContent = ok ? '已复制到剪贴板。' : '复制失败，请手动复制。';
      };
    }

    const aiBtn = document.getElementById('job-fit-ai-analyze');
    if (aiBtn) {
      aiBtn.onclick = async () => {
        if (jobFitAiLoading || !jobFitLastResult) return;

        jobFitAiLoading = true;
        jobFitAiError = '';
        jobFitAiResult = null;
        renderJobFitPanel(jobFitLastResult);

        try {
          const payload = buildAiAnalyzePayload(jobFitLastResult.jobInfo, jobFitLastResult);
          jobFitAiResult = await callAiAnalyzeBackend(payload);
        } catch (e) {
          jobFitAiError = '后端未启动或接口调用失败。请确认 backend 服务已在 http://localhost:8080 启动。';
        } finally {
          jobFitAiLoading = false;
          renderJobFitPanel(jobFitLastResult);
        }
      };
    }
  }

  function updateJobFitPanel() {
    const detailContainer = findJobDetailContainer();
    const jobInfo = extractJobInfoFromDetail(detailContainer);
    if (!jobInfo.jdText || jobInfo.jdText.length < 30) return;
    renderJobFitPanel(scoreJob(jobInfo));
  }

  function observeJobDetailChanges() {
    const debouncedUpdate = () => {
      clearTimeout(jobFitTimer);
      jobFitTimer = setTimeout(updateJobFitPanel, 500);
    };

    setTimeout(updateJobFitPanel, 1500);

    const observer = new MutationObserver(debouncedUpdate);
    observer.observe(document.body, {
      childList: true,
      subtree: true,
      characterData: true
    });
  }

  function addButton() {
    if (document.getElementById('boss-export-btn')) return;

    const btn = document.createElement('button');
    btn.id = 'boss-export-btn';
    btn.innerText = '导出聊天列表';
    btn.style.cssText = `
      position: fixed;
      left: 20px;
      top: 45px;
      z-index: 999999;
      background: #00bebd;
      color: #fff;
      border: none;
      border-radius: 8px;
      padding: 10px 14px;
      font-size: 14px;
      cursor: pointer;
      box-shadow: 0 2px 10px rgba(0,0,0,.2);
    `;

    btn.onclick = async () => {
      btn.innerText = '正在导出...';
      btn.disabled = true;

      try {
        await exportBossChat();
      } finally {
        btn.innerText = '导出聊天列表';
        btn.disabled = false;
      }
    };

    document.body.appendChild(btn);
  }

  setTimeout(addButton, 2000);
  setInterval(addButton, 3000);
  observeJobDetailChanges();

  window.JobFitScoring = {
    getVisibleJobText,
    findJobDetailContainer,
    findJobHeaderBlock,
    extractJobTitleFromHeader,
    extractSalaryFromHeader,
    extractSalaryByGeometry,
    extractCityFromMetaLine,
    cleanJobTitle,
    isSearchConditionText,
    findJobTitleElement,
    getElementOwnText,
    collectVisibleSalaryCandidates,
    findVisibleTextNodes,
    isVisibleElement,
    isLikelySalaryText,
    extractJobInfoFromDetail,
    parseSalary,
    parseExperience,
    parseEducation,
    parseScheduleAndDuration,
    extractTags,
    scoreJob,
    detectDirection,
    detectRiskFlags,
    detectMatchedKeywords,
    getConclusion,
    getGreetingType,
    buildAiAnalyzePayload,
    callAiAnalyzeBackend,
    renderAiAnalyzeResult,
    renderJobFitPanel,
    observeJobDetailChanges
  };
})();

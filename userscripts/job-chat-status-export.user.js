// ==UserScript==
// @name         Job Chat Status Export Helper
// @namespace    job-chat-status-export-helper
// @version      1.2.0
// @description  Export visible job chat status from the current browser page into TSV format for personal job-search follow-up.
// @match        https://www.zhipin.com/*
// @run-at       document-end
// @grant        GM_setClipboard
// ==/UserScript==

(function () {
  'use strict';

  /**
   * Job Chat Status Export Helper
   *
   * 定位：个人求职信息整理工具。
   * 只读取当前登录用户在网页聊天列表里已经能看到的 DOM 文本。
   * 不请求内部接口、不绕过验证码、不自动投递、不自动打招呼、不自动回复。
   */

  const MAX_SCROLLS = 12; // 想抓更多联系人，可以改成 20。建议低频手动使用，不要高频循环。

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
})();

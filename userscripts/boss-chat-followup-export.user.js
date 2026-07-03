// ==UserScript==
// @name         BOSS Follow-up Status Export
// @namespace    ai-job-screening-agent-followup
// @version      1.1.0
// @description  Optional Follow-up Export for visible communication status on the current BOSS page.
// @match        https://www.zhipin.com/*
// @run-at       document-end
// @grant        GM_setClipboard
// ==/UserScript==

(function () {
  'use strict';

  /**
   * Optional Follow-up Export.
   *
   * Reads only visible DOM text from the current page and exports communication
   * status for manual follow-up. It does not read Cookie / Token, access
   * non-public APIs, auto-send messages, or auto-apply to jobs.
   */

  const MAX_SCROLLS = 12;

  const ROLE_WORDS = [
    '人事行政主管', '人力资源主管', '人力资源HR', '人力主管',
    'HRBP专员', '招聘人员', '招聘主管', '招聘专员', '招聘者', '招聘官',
    '人事专员', '人事主管', '人事经理', '人事行政', '人力总监',
    '公司负责人', '区域经理', '总经理', '架构师',
    'HRBP', 'HRM', 'hrbp', 'CEO', 'HR', '人事'
  ].sort((a, b) => b.length - a.length);

  const COMPANY_START_HINTS = [
    '北京', '上海', '深圳', '广州', '杭州', '天津', '南京', '苏州',
    '成都', '武汉', '西安', '重庆', '厦门', '青岛', '合肥',
    '中', '国', '华', '新', '云', '智', '数', '科', '网',
    '信息', '科技', '智能', '网络', '软件', '数据', '云计算',
    '示例', '样例', '测试', '某某'
  ];

  const MSG_START_RE = /^(感谢|您好|你好|很遗憾|不好意思|抱歉|方便|可以|请问|这边|目前|简历|先发|稍等|我们|岗位|什么时候|时间|收到|已收到|发我|加微信|电话|面试)/;

  const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

  const clean = (value) => String(value || '')
    .replace(/\s+/g, ' ')
    .trim();

  const compact = (value) => clean(value).replace(/[|\s]+/g, '');

  function escapeRegExp(value) {
    return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  function displayRole(role) {
    const lower = role.toLowerCase();
    if (lower === 'hr') return 'HR';
    if (lower === 'hrbp') return 'HRBP';
    if (lower === 'hrm') return 'HRM';
    if (lower === 'ceo') return 'CEO';
    return role;
  }

  function startsWithAny(value, hints) {
    return hints.some(hint => value.startsWith(hint));
  }

  function findRoleAtEnd(headerCompact) {
    const lower = headerCompact.toLowerCase();
    for (const role of ROLE_WORDS) {
      const roleLower = role.toLowerCase();
      if (lower.endsWith(roleLower)) {
        return {
          role: displayRole(role),
          left: headerCompact.slice(0, headerCompact.length - role.length)
        };
      }
    }
    return { role: '', left: headerCompact };
  }

  function splitNameCompany(leftCompact) {
    const value = compact(leftCompact);
    if (!value) return { name: '', company: '' };

    const honorMatch = value.match(/^(.{1,6}?(女士|先生|小姐|老师))(.+)$/);
    if (honorMatch) {
      return {
        name: honorMatch[1],
        company: honorMatch[3]
      };
    }

    for (const nameLength of [4, 3, 2]) {
      if (value.length > nameLength) {
        const possibleCompany = value.slice(nameLength);
        if (startsWithAny(possibleCompany, COMPANY_START_HINTS)) {
          return {
            name: value.slice(0, nameLength),
            company: possibleCompany
          };
        }
      }
    }

    if (value.length > 3) {
      return {
        name: value.slice(0, 3),
        company: value.slice(3)
      };
    }

    return { name: value, company: '' };
  }

  function parseHeader(headerText) {
    const headerCompact = compact(headerText);
    if (!headerCompact) return { name: '', company: '', role: '' };

    const roleInfo = findRoleAtEnd(headerCompact);
    const nameCompany = splitNameCompany(roleInfo.left);

    return {
      name: nameCompany.name,
      company: nameCompany.company,
      role: roleInfo.role
    };
  }

  function roleBoundaryPattern(role) {
    return role.split('').map(escapeRegExp).join('\\s*');
  }

  function splitByRoleBoundary(body) {
    const candidates = [];
    for (const role of ROLE_WORDS) {
      const regex = new RegExp(roleBoundaryPattern(role), 'gi');
      let match;
      while ((match = regex.exec(body)) !== null) {
        const end = match.index + match[0].length;
        const before = clean(body.slice(0, end));
        const after = clean(body.slice(end));
        if (before.length < 3 || before.length > 80) continue;
        if (!after || MSG_START_RE.test(after) || after.startsWith('[')) {
          candidates.push({ end, role, roleLength: role.length });
        }
      }
    }

    if (!candidates.length) return null;
    candidates.sort((a, b) => {
      if (a.end !== b.end) return a.end - b.end;
      return b.roleLength - a.roleLength;
    });

    const best = candidates[0];
    return {
      head: clean(body.slice(0, best.end)),
      msg: clean(body.slice(best.end))
    };
  }

  function fallbackSplitMessage(body) {
    const match = body.match(/\s(感谢|您好|你好|很遗憾|不好意思|抱歉|方便|可以|请问|这边|目前|简历|先发|稍等|我们|岗位|什么时候|时间|收到|已收到|发我|加微信|电话|面试)/);
    if (!match) return { head: body, msg: '' };
    return {
      head: clean(body.slice(0, match.index)),
      msg: clean(body.slice(match.index))
    };
  }

  function buildAction(status, rawText) {
    const text = rawText || '';
    if (/很遗憾|不合适|不匹配|暂时不考虑|不太符合|暂无合适/.test(text)) {
      return {
        actionLevel: 'P9',
        nextStep: '拒绝/不用跟进'
      };
    }

    if (status === '对方回复/无状态') {
      if (/方便|可以|约|面试|电话|微信|简历|发.*简历|聊一聊|什么时候|时间/.test(text)) {
        return {
          actionLevel: 'P0',
          nextStep: '优先查看并人工回复'
        };
      }
      return {
        actionLevel: 'P0',
        nextStep: '点进去查看对方回复'
      };
    }

    if (status === '已读') {
      return {
        actionLevel: 'P1',
        nextStep: '已读未回，稍后可人工跟进'
      };
    }

    if (status === '送达' || status === '未读') {
      return {
        actionLevel: 'P2',
        nextStep: '先等待，不自动追问'
      };
    }

    return {
      actionLevel: 'P3',
      nextStep: '普通等待'
    };
  }

  function parseRaw(rawText) {
    const raw = clean(rawText);
    const timeMatch = raw.match(/(刚刚|\d{1,2}:\d{2}|昨天|前天|\d{1,2}-\d{1,2}|\d{1,2}月\d{1,2}日)/);
    const time = timeMatch ? timeMatch[1] : '';

    let body = raw;
    if (timeMatch) {
      body = clean(raw.slice(0, timeMatch.index) + ' ' + raw.slice(timeMatch.index + time.length));
    }

    const statusMatch = body.match(/\[(送达|已读|未读)\]|(送达|已读|未读)/);
    const status = statusMatch ? (statusMatch[1] || statusMatch[2]) : '对方回复/无状态';

    let headText = '';
    let lastMsg = '';

    if (statusMatch) {
      headText = clean(body.slice(0, statusMatch.index));
      lastMsg = clean(body.slice(statusMatch.index + statusMatch[0].length));
    } else {
      const split = splitByRoleBoundary(body) || fallbackSplitMessage(body);
      headText = split.head;
      lastMsg = split.msg;
    }

    const parsed = parseHeader(headText);
    let suggest = status === '对方回复/无状态' ? '优先点开查看' : '等待';

    if (/很遗憾|不合适|不匹配|暂时不考虑|不太符合|暂无合适/.test(raw)) {
      suggest = '可能拒绝/低优先级';
    } else if (/方便|可以|约|面试|电话|微信|简历|发.*简历|聊一聊|什么时候|时间/.test(raw) && status === '对方回复/无状态') {
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

  const parseFollowupStatus = parseRaw;

  function isVisibleElement(el) {
    if (!el) return false;
    const style = window.getComputedStyle(el);
    if (style.display === 'none' || style.visibility === 'hidden') return false;
    const rect = el.getBoundingClientRect();
    return rect.width > 1 && rect.height > 1 && rect.bottom >= 0 && rect.top <= window.innerHeight;
  }

  function getVisibleText(el) {
    if (!isVisibleElement(el)) return '';
    return clean(el.innerText || el.textContent || '');
  }

  function getScrollBox() {
    const candidates = Array.from(document.querySelectorAll('div'))
      .map(el => ({
        el,
        rect: el.getBoundingClientRect(),
        scrollHeight: el.scrollHeight,
        clientHeight: el.clientHeight
      }))
      .filter(item =>
        item.rect.left >= 0 &&
        item.rect.left < 760 &&
        item.rect.width > 240 &&
        item.rect.height > 260 &&
        item.scrollHeight > item.clientHeight + 100
      )
      .sort((a, b) => (b.scrollHeight - b.clientHeight) - (a.scrollHeight - a.clientHeight));

    return candidates.length ? candidates[0].el : null;
  }

  function looksLikeFollowupRow(text) {
    return /(送达|已读|未读|刚刚|\d{1,2}:\d{2}|昨天|前天|\d{1,2}-\d{1,2}|HR|HRBP|招聘|人事|人力资源|沟通|面试|简历|不合适|方便|您好|你好)/.test(text);
  }

  function collectVisibleItems() {
    const rows = [];
    const seen = new Set();

    for (const el of Array.from(document.querySelectorAll('div, li, a'))) {
      const rect = el.getBoundingClientRect();
      if (rect.left < 0 || rect.left > 760) continue;
      if (rect.top < 35 || rect.bottom > window.innerHeight + 40) continue;
      if (rect.width < 220 || rect.width > 760) continue;
      if (rect.height < 32 || rect.height > 180) continue;

      const text = getVisibleText(el);
      if (!text || text.length < 6 || text.length > 500) continue;
      if (!looksLikeFollowupRow(text)) continue;

      const key = text.replace(/\s+/g, '');
      if (seen.has(key)) continue;
      seen.add(key);

      rows.push({
        rawText: text,
        top: rect.top
      });
    }

    return rows.sort((a, b) => a.top - b.top).map(row => row.rawText);
  }

  function toTsv(rows) {
    const headers = ['联系人', '公司', '身份', '时间', '状态', '行动等级', '下一步', '建议', '最后消息', '原始文本'];
    return [
      headers.join('\t'),
      ...rows.map(row => headers.map(header => String(row[header] || '').replace(/\t|\n/g, ' ')).join('\t'))
    ].join('\n');
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

  function showResult(text, count, copied) {
    const old = document.getElementById('boss-followup-export-panel');
    if (old) old.remove();

    const panel = document.createElement('div');
    panel.id = 'boss-followup-export-panel';
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
        <b>沟通状态导出结果 / Follow-up Status Export</b>
        <button id="boss-followup-export-close" style="border:none;background:#eee;padding:4px 8px;border-radius:6px;cursor:pointer;">关闭</button>
      </div>
      <div style="margin-bottom:8px;color:#666;">
        已整理 ${count} 条可见沟通状态。${copied ? '已复制到剪贴板。' : '自动复制失败，请手动复制下方内容。'}
      </div>
      <textarea id="boss-followup-export-textarea" style="width:100%;height:360px;box-sizing:border-box;font-size:12px;"></textarea>
    `;

    document.body.appendChild(panel);
    const textarea = document.getElementById('boss-followup-export-textarea');
    textarea.value = text;
    textarea.focus();
    textarea.select();
    document.getElementById('boss-followup-export-close').onclick = () => panel.remove();
  }

  async function exportFollowupStatus() {
    const scrollBox = getScrollBox();
    const all = [];
    const seen = new Set();

    if (scrollBox) {
      scrollBox.scrollTop = 0;
      await sleep(600);
    }

    for (let i = 0; i < MAX_SCROLLS; i++) {
      for (const item of collectVisibleItems()) {
        const key = item.replace(/\s+/g, '');
        if (!seen.has(key)) {
          seen.add(key);
          all.push(item);
        }
      }

      if (scrollBox) {
        scrollBox.scrollBy(0, scrollBox.clientHeight * 0.85);
      } else {
        window.scrollBy(0, window.innerHeight * 0.85);
      }
      await sleep(450);
    }

    const rows = all.map(parseRaw);
    const tsv = toTsv(rows);
    showResult(tsv, rows.length, copyText(tsv));
  }

  function addExportButton() {
    if (!/\/web\/geek\/chat|\/chat/.test(location.pathname)) return;
    if (document.getElementById('boss-followup-export-btn')) return;

    const btn = document.createElement('button');
    btn.id = 'boss-followup-export-btn';
    btn.innerText = '导出沟通状态';
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
        await exportFollowupStatus();
      } finally {
        btn.innerText = '导出沟通状态';
        btn.disabled = false;
      }
    };

    document.body.appendChild(btn);
  }

  setTimeout(addExportButton, 2000);
  setInterval(addExportButton, 3000);
})();

import { SplashScreen } from '@capacitor/splash-screen';
import { wireframes } from './handshapes.js';
import { installBackDrag } from './back-drag.js';

document.querySelectorAll('.screen-back').forEach(installBackDrag);

const letters = [...'ABCDEFGHIJKLMNOPQRSTUVWXYZ'];
// One place to say which letters can be practised. J and Z need motion, which the classifier cannot judge yet;
// add a letter here (or remove it) to grey it out. Wireframes come from ./handshapes.js (see scripts/gen-web-wireframes.py).
const letterStatus = { J: 'motion', Z: 'motion' };
const isPracticable = letter => !letterStatus[letter];
const practicable = letters.filter(isPracticable);
const storageKey = 'sign-by-sign-progress-v1';
const nativeAckKey = 'sign-by-sign-native-ack-v1';
const native = window.HandspellBridge;
const shell = document.querySelector('.app-shell');
const list = document.querySelector('#letter-list');
const scroller = document.querySelector('#letter-scroll');
const index = document.querySelector('#alphabet-index');
const modal = document.querySelector('#practice-modal');
const setup = document.querySelector('#setup-screen');
const nextScreen = document.querySelector('#next-screen');
const utility = document.querySelector('#utility-screen');
const phoneAnimation = document.querySelector('#phone-animation');
let selectedLetter = null;
let showSetupAfterClose = false;
let utilityTrigger = null;
let activeIndex = -1;
let scrubbing = false;
let scrubPointerId = null;
let previousFocus = null;

function readProgress() {
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey));
    return {
      completed: Array.isArray(saved?.completed) ? [...new Set(saved.completed.filter(letter => practicable.includes(letter)))] : [],
      streak: Number.isInteger(saved?.streak) && saved.streak >= 0 ? saved.streak : 0,
      lastPractice: typeof saved?.lastPractice === 'string' ? saved.lastPractice : null,
    };
  } catch {
    return { completed: [], streak: 0, lastPractice: null };
  }
}

const progress = readProgress();
const localDay = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

function saveProgress() {
  try {
    localStorage.setItem(storageKey, JSON.stringify(progress));
    document.querySelector('#save-error').hidden = true;
  } catch {
    document.querySelector('#save-error').hidden = false;
  }
  reportCompleted();
}

// The native main menu counts completed letters, so the page tells it after every save (and once on load).
function reportCompleted() {
  try { native?.reportCompleted?.(JSON.stringify(progress.completed)); } catch { /* the page works without it */ }
}

function renderCards() {
  document.querySelector('#completion-count').textContent = `${progress.completed.length} / ${practicable.length}`;
  list.innerHTML = letters.map((letter, i) => {
    const done = progress.completed.includes(letter);
    const available = isPracticable(letter);
    const label = available
      ? `Practice letter ${letter}${done ? ', complete' : ''}`
      : `Letter ${letter}, not available yet: it needs motion`;
    return `<button class="letter-card${done ? ' done' : ''}${available ? '' : ' unavailable'}" id="letter-${letter}" data-letter="${letter}" type="button" aria-label="${label}"${available ? '' : ' aria-disabled="true"'}>
      <span class="card-letter" aria-hidden="true">${letter}</span>
      <span class="card-meta">${cardMeta(letter, done)}</span>
      ${signArt(letter)}
    </button>`;
  }).join('');
  updateWave();
}

function cardMeta(letter, done) {
  if (!isPracticable(letter)) return 'needs motion';
  return done ? 'complete' : String(letters.indexOf(letter) + 1).padStart(2, '0');
}

function signArt(letter) {
  const shape = wireframes[letter];
  if (!shape) {
    // No exemplar for this letter yet: keep the neutral reserved slot (not a handshape).
    return `<span class="sign-placeholder" data-sign-letter="${letter}" aria-hidden="true"><svg viewBox="0 0 64 64" fill="none"><path d="M13 22v-9h9m20 0h9v9M13 42v9h9m20 0h9v-9"/><circle cx="32" cy="32" r="4"/></svg></span>`;
  }
  const dots = shape.dots.map(([x, y]) => `<circle cx="${x}" cy="${y}" r="1.7"/>`).join('');
  return `<span class="sign-placeholder wireframe" data-sign-letter="${letter}" aria-hidden="true"><svg viewBox="0 0 64 64" fill="none"><path d="${shape.d}"/>${dots}</svg></span>`;
}

function updateCard(letter) {
  const card = document.querySelector(`#letter-${letter}`);
  const done = progress.completed.includes(letter);
  card.classList.toggle('done', done);
  card.setAttribute('aria-label', `Practice letter ${letter}${done ? ', complete' : ''}`);
  card.querySelector('.card-meta').textContent = cardMeta(letter, done);
  document.querySelector('#completion-count').textContent = `${progress.completed.length} / ${practicable.length}`;
}

index.innerHTML = letters.map((letter, i) => `<button class="index-cell" type="button" data-index="${i}" aria-label="Jump to letter ${letter}"><span class="index-glyph">${letter}</span></button>`).join('');
index.insertAdjacentHTML('afterbegin', '<svg class="index-surface" aria-hidden="true" preserveAspectRatio="none"><path></path></svg>');
const outline = index.querySelector('.index-surface');
const outlinePath = outline.querySelector('path');
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
let glassFocus = 0;
let glassAmount = 0;
let glassTargetFocus = 0;
let glassTargetAmount = 0;
let glassFrame = 0;
let lastFrameTime = 0;

function drawGlass() {
  const height = index.clientHeight;
  if (!height) return;
  const middle = ((glassFocus + .5) / letters.length) * height;
  const sigma = (height / letters.length) * 1.35;
  const leftAt = y => 38 - 15 * glassAmount * Math.exp(-((y - middle) ** 2) / (2 * sigma * sigma));
  const points = [];
  for (let y = 14; y <= height - 14; y += 4) points.push(`L ${leftAt(y).toFixed(2)} ${y.toFixed(2)}`);
  points.push(`L ${leftAt(height - 14).toFixed(2)} ${(height - 14).toFixed(2)}`);
  const topLeft = leftAt(0);
  const bottomLeft = leftAt(height);
  const shape = `M ${(topLeft + 14).toFixed(2)} 0 Q ${topLeft.toFixed(2)} 0 ${leftAt(14).toFixed(2)} 14 ${points.join(' ')} Q ${bottomLeft.toFixed(2)} ${height} ${(bottomLeft + 14).toFixed(2)} ${height} L 55 ${height} Q 69 ${height} 69 ${height - 14} L 69 14 Q 69 0 55 0 Z`;
  outline.setAttribute('viewBox', `0 0 69 ${height}`);
  outlinePath.setAttribute('d', shape);
  updateCardWave(index.getBoundingClientRect().top + middle, sigma);
}

function animateGlass(time) {
  const elapsed = lastFrameTime ? Math.min(40, time - lastFrameTime) : 16;
  lastFrameTime = time;
  const blend = 1 - Math.exp(-elapsed / 65);
  glassFocus += (glassTargetFocus - glassFocus) * blend;
  glassAmount += (glassTargetAmount - glassAmount) * blend;
  drawGlass();
  if (Math.abs(glassTargetFocus - glassFocus) > .01 || Math.abs(glassTargetAmount - glassAmount) > .01) {
    glassFrame = requestAnimationFrame(animateGlass);
  } else {
    glassFocus = glassTargetFocus;
    glassAmount = glassTargetAmount;
    drawGlass();
    glassFrame = 0;
    lastFrameTime = 0;
  }
}

function setGlassTarget(focus, amount) {
  glassTargetFocus = focus;
  glassTargetAmount = amount;
  if (reducedMotion.matches) {
    if (glassFrame) cancelAnimationFrame(glassFrame);
    glassFrame = 0;
    glassFocus = focus;
    glassAmount = amount;
    drawGlass();
    return;
  }
  if (!glassFrame) glassFrame = requestAnimationFrame(animateGlass);
}

// Native haptics via the Android bridge (navigator.vibrate is unreliable in WebView); the browser API is only a fallback.
function tick() {
  if (native?.tick) native.tick();
  else if ('vibrate' in navigator) navigator.vibrate(8);
}

function confirmTick() {
  if (native?.confirm) native.confirm();
  else if ('vibrate' in navigator) navigator.vibrate(14);
}

function updateCardWave(curveY, sigma) {
  const cards = [...list.querySelectorAll('.letter-card')];
  if (glassAmount < .001) {
    cards.forEach(card => card.style.removeProperty('width'));
    return;
  }
  const maximum = list.clientWidth;
  const cardCenters = cards.map(card => {
    const rect = card.getBoundingClientRect();
    return rect.top + rect.height / 2;
  });
  cards.forEach((card, n) => {
    const distance = cardCenters[n] - curveY;
    const wave = glassAmount * Math.exp(-(distance * distance) / (2 * sigma * sigma));
    card.style.width = `${Math.max(0, maximum - 26 * wave)}px`;
  });
}

function updateWave() {
  list.querySelectorAll('.letter-card').forEach((card, n) => {
    card.classList.toggle('selected', scrubbing && n === activeIndex);
  });
  index.querySelectorAll('.index-cell').forEach((cell, n) => {
    const distance = Math.abs(n - activeIndex);
    const wave = !scrubbing || activeIndex < 0 ? 0 : Math.exp(-(distance * distance) / (2 * 1.3 * 1.3));
    const direction = document.documentElement.classList.contains('left-handed') ? 1 : -1;
    cell.style.setProperty('--push', `${(direction * 12 * wave).toFixed(2)}px`);
    cell.style.setProperty('--scale', (1 + .14 * wave).toFixed(3));
    cell.classList.toggle('active', scrubbing && n === activeIndex);
  });
}

function centerCard(i) {
  const card = document.querySelector(`#letter-${letters[i]}`);
  const centerOffset = (scroller.clientHeight - card.offsetHeight) / 2;
  const top = scroller.scrollTop + card.getBoundingClientRect().top - scroller.getBoundingClientRect().top - centerOffset;
  const limit = Math.max(0, scroller.scrollHeight - scroller.clientHeight);
  scroller.scrollTo({ top: Math.max(0, Math.min(limit, top)), behavior: 'auto' });
}

let touchStartY = null;
scroller.addEventListener('touchstart', event => {
  touchStartY = event.touches[0]?.clientY ?? null;
}, { passive: true });
scroller.addEventListener('touchmove', event => {
  if (touchStartY === null) return;
  const distance = event.touches[0].clientY - touchStartY;
  const atStart = scroller.scrollTop <= 0 && distance > 0;
  const atEnd = scroller.scrollTop >= scroller.scrollHeight - scroller.clientHeight - 1 && distance < 0;
  const pull = atStart || atEnd ? Math.sign(distance) * Math.min(20, Math.abs(distance) * .2) : 0;
  list.classList.toggle('pulling', pull !== 0);
  list.style.setProperty('--pull', `${pull}px`);
}, { passive: true });
function releasePull() {
  touchStartY = null;
  list.classList.remove('pulling');
  list.style.setProperty('--pull', '0px');
}
scroller.addEventListener('touchend', releasePull, { passive: true });
scroller.addEventListener('touchcancel', releasePull, { passive: true });

function selectIndex(i, { haptic = false } = {}) {
  i = Math.max(0, Math.min(letters.length - 1, i));
  if (i !== activeIndex && haptic) tick();
  activeIndex = i;
  updateWave();
  centerCard(i);
  setGlassTarget(i, scrubbing ? 1 : 0);
}

function indexAt(clientY) {
  const rect = index.getBoundingClientRect();
  return Math.max(0, Math.min(letters.length - 1, Math.floor(((clientY - rect.top) / rect.height) * letters.length)));
}

index.addEventListener('pointerdown', event => {
  if (scrubPointerId !== null) return;
  scrubPointerId = event.pointerId;
  scrubbing = true;
  index.setPointerCapture(event.pointerId);
  selectIndex(indexAt(event.clientY), { haptic: true });
  event.preventDefault();
});
index.addEventListener('pointermove', event => {
  if (event.pointerId !== scrubPointerId) return;
  const nextIndex = indexAt(event.clientY);
  if (nextIndex !== activeIndex) selectIndex(nextIndex, { haptic: true });
});
function endScrub(event) {
  if (event.pointerId !== scrubPointerId) return;
  scrubPointerId = null;
  scrubbing = false;
  updateWave();
  setGlassTarget(activeIndex < 0 ? 0 : activeIndex, 0);
}
index.addEventListener('pointerup', endScrub);
index.addEventListener('pointercancel', endScrub);
index.addEventListener('lostpointercapture', endScrub);
index.addEventListener('click', event => {
  const cell = event.target.closest('.index-cell');
  if (cell && event.detail === 0) selectIndex(Number(cell.dataset.index), { haptic: true });
});

list.addEventListener('click', event => {
  const card = event.target.closest('.letter-card');
  if (card && isPracticable(card.dataset.letter)) openPractice(card.dataset.letter);
});

function openPractice(letter) {
  clearTimeout(closeTimer);
  closeTimer = null;
  showSetupAfterClose = false;
  selectedLetter = letter;
  previousFocus = document.activeElement;
  document.querySelector('#practice-letter').textContent = letter;
  document.querySelector('#practice-title').textContent = `Letter ${letter}`;
  // A complete letter stays fully practicable: Practice is always offered; only the second action changes.
  const completed = progress.completed.includes(letter);
  document.querySelector('#practice-status').hidden = !completed;
  document.querySelector('#complete-practice').hidden = completed;
  document.querySelector('#undo-completion').hidden = !completed;
  modal.hidden = false;
  modal.classList.remove('closing');
  void modal.offsetHeight;
  modal.classList.add('open');
  shell.inert = true;
  document.querySelector('#close-practice').focus();
}

let closeTimer = null;
function finishClose() {
  if (modal.classList.contains('open')) return;
  clearTimeout(closeTimer);
  closeTimer = null;
  modal.hidden = true;
  modal.classList.remove('closing');
  if (showSetupAfterClose) {
    showSetupAfterClose = false;
    phoneAnimation.src = './phone-stand-animation.html';
    setup.hidden = false;
    document.querySelector('#close-setup').focus();
  } else {
    shell.inert = false;
    previousFocus?.focus();
  }
}

function closePractice() {
  if (modal.hidden) return;
  modal.classList.add('closing');
  modal.classList.remove('open');
  selectedLetter = null;
  clearTimeout(closeTimer);
  closeTimer = setTimeout(finishClose, 320);
}

modal.querySelector('.practice-sheet').addEventListener('transitionend', event => {
  if (event.propertyName === 'transform') finishClose();
});

document.querySelector('#close-practice').addEventListener('click', closePractice);
function openUtility(name, trigger) {
  utilityTrigger = trigger;
  utility.setAttribute('aria-label', name);
  utility.hidden = false;
  shell.inert = true;
  document.querySelector('#utility-back').focus();
}
function closeUtility() {
  utility.hidden = true;
  shell.inert = false;
  utilityTrigger?.focus();
}
document.querySelector('#open-settings').addEventListener('click', event => openUtility('Settings', event.currentTarget));
document.querySelector('#open-paper').addEventListener('click', event => openUtility('Paper', event.currentTarget));
document.querySelector('#utility-back').addEventListener('click', closeUtility);
function closeSetup() {
  setup.hidden = true;
  phoneAnimation.removeAttribute('src');
  shell.inert = false;
  previousFocus?.focus();
}
document.querySelector('#close-setup').addEventListener('click', closeSetup);
document.querySelector('#confirm-setup').addEventListener('click', () => {
  setup.hidden = true;
  phoneAnimation.removeAttribute('src');
  nextScreen.hidden = false;
  document.querySelector('#back-next').focus();
});
function closeNext() {
  nextScreen.hidden = true;
  shell.inert = false;
  previousFocus?.focus();
}
document.querySelector('#back-next').addEventListener('click', closeNext);
modal.addEventListener('click', event => { if (event.target === modal) closePractice(); });
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && !modal.hidden) closePractice();
  else if (event.key === 'Escape' && !nextScreen.hidden) closeNext();
  else if (event.key === 'Escape' && !setup.hidden) closeSetup();
  else if (event.key === 'Escape' && !utility.hidden) closeUtility();
  if (event.key === 'Tab' && !nextScreen.hidden) {
    document.querySelector('#back-next').focus();
    event.preventDefault();
  }
  if (event.key === 'Tab' && !utility.hidden) {
    document.querySelector('#utility-back').focus();
    event.preventDefault();
  }
  if (event.key === 'Tab' && !setup.hidden && modal.hidden) {
    const first = document.querySelector('#close-setup');
    const confirm = document.querySelector('#confirm-setup');
    const last = confirm.hidden ? first : confirm;
    if (event.shiftKey && document.activeElement === first) { last.focus(); event.preventDefault(); }
    else if (!event.shiftKey && document.activeElement === last) { first.focus(); event.preventDefault(); }
  }
  if (event.key === 'Tab' && !modal.hidden) {
    const first = document.querySelector('#close-practice');
    const last = progress.completed.includes(selectedLetter)
      ? document.querySelector('#undo-completion')
      : document.querySelector('#complete-practice');
    if (event.shiftKey && document.activeElement === first) { last.focus(); event.preventDefault(); }
    else if (!event.shiftKey && document.activeElement === last) { first.focus(); event.preventDefault(); }
  }
});
// System back: close the topmost sheet or screen and report true; with nothing open, report false so the app
// returns to the main menu.
window.aslHandleBack = () => {
  if (!modal.hidden) closePractice();
  else if (!nextScreen.hidden) closeNext();
  else if (!setup.hidden) closeSetup();
  else if (!utility.hidden) closeUtility();
  else return false;
  return true;
};
window.addEventListener('aslNativeBack', () => { window.aslHandleBack(); });
document.querySelector('#alphabet-back').addEventListener('click', () => {
  if (native?.exitMenu) native.exitMenu();
  else history.back();
});

document.querySelector('#complete-practice').addEventListener('click', () => {
  if (!selectedLetter) return;
  const today = localDay(new Date());
  if (progress.lastPractice !== today) {
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    progress.streak = progress.lastPractice === localDay(yesterday) ? progress.streak + 1 : 1;
    progress.lastPractice = today;
  }
  if (!progress.completed.includes(selectedLetter)) progress.completed.push(selectedLetter);
  saveProgress();
  const letter = selectedLetter;
  updateCard(letter);
  closePractice();
  confirmTick();
  // The app shows its reward card over the page and counts the letter for today.
  try { native?.markedComplete?.(letter); } catch { /* the page works without it */ }
});

document.querySelector('#start-practice').addEventListener('click', () => {
  if (!selectedLetter) return;
  showSetupAfterClose = true;
  closePractice();
});

document.querySelector('#undo-completion').addEventListener('click', () => {
  if (!selectedLetter) return;
  const letter = selectedLetter;
  progress.completed = progress.completed.filter(completedLetter => completedLetter !== letter);
  saveProgress();
  updateCard(letter);
  closePractice();
});

// The placement screen has a Confirm button again. Set this to true to have the animation move on by itself once
// the phone has turned twice (phone-stand-animation.html posts 'aslSetupComplete'); the button is then hidden.
const autoAdvanceAfterSpins = false;
const confirmSetup = document.querySelector('#confirm-setup');
window.addEventListener('message', event => {
  if (event.source !== phoneAnimation.contentWindow || event.data?.type !== 'aslSetupComplete') return;
  if (autoAdvanceAfterSpins && !setup.hidden && !reducedMotion.matches) confirmSetup.click();
});
function revealConfirmIfStatic() { confirmSetup.hidden = autoAdvanceAfterSpins && !reducedMotion.matches; }
revealConfirmIfStatic();
reducedMotion.addEventListener?.('change', revealConfirmIfStatic);

// Letters the camera has confirmed count as complete here too. Each native match total is merged once, so
// "Undo completion" sticks until the camera confirms the letter again.
function syncNativeMatches() {
  if (!native?.cameraMatches) return;
  let matches;
  let acked;
  try { matches = JSON.parse(native.cameraMatches()); } catch { return; }
  try { acked = JSON.parse(localStorage.getItem(nativeAckKey)) || {}; } catch { acked = {}; }
  let changed = false;
  for (const [letter, count] of Object.entries(matches)) {
    if (!practicable.includes(letter) || !(count > (acked[letter] || 0))) continue;
    acked[letter] = count;
    if (!progress.completed.includes(letter)) progress.completed.push(letter);
    changed = true;
  }
  if (!changed) return;
  try { localStorage.setItem(nativeAckKey, JSON.stringify(acked)); } catch { /* progress still updates in memory */ }
  saveProgress();
  practicable.forEach(updateCard);
}
window.addEventListener('aslNativeProgress', syncNativeMatches);

function syncNativeStreak() {
  const chip = document.querySelector('#open-progress');
  if (!native?.streak) { chip.hidden = true; return; }
  let streak;
  try { streak = JSON.parse(native.streak()); } catch { chip.hidden = true; return; }
  if (!Number.isInteger(streak.current) || streak.current <= 0) { chip.hidden = true; return; }
  chip.hidden = false;
  chip.classList.toggle('pending', !streak.today);
  chip.textContent = streak.today
    ? `${streak.current}-day streak`
    : `${streak.current}-day streak · practise today to keep it`;
  chip.setAttribute('aria-label', `${streak.current}-day streak`);
}
window.addEventListener('aslNativeProgress', syncNativeStreak);
document.addEventListener('visibilitychange', () => { if (!document.hidden) syncNativeStreak(); });

function syncHandedness() {
  if (!native?.handedness) return;
  let leftHanded;
  try { leftHanded = native.handedness() === 'left'; } catch { return; }
  document.documentElement.classList.toggle('left-handed', leftHanded);
  updateWave();
  drawGlass();
}
window.addEventListener('aslNativeProgress', syncHandedness);

// Light or dark, from the app's Theme setting (the main menu's toggle or Settings), applied as soon as it changes.
function syncTheme() {
  if (!native?.theme) return;
  let light;
  try { light = native.theme() === 'light'; } catch { return; }
  document.documentElement.classList.toggle('theme-light', light);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', light ? '#F5F5F7' : '#1D1D1F');
}
window.addEventListener('aslNativeProgress', syncTheme);
document.addEventListener('visibilitychange', () => { if (!document.hidden) syncTheme(); });

syncTheme();
renderCards();
syncNativeMatches();
reportCompleted();
syncNativeStreak();
syncHandedness();
drawGlass();
window.addEventListener('resize', () => { updateWave(); drawGlass(); });
SplashScreen.hide().catch(() => {});

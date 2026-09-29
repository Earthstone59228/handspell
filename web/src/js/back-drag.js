// Keep the hit target still while the chevron follows a finger with a little resistance.
export function installBackDrag(button) {
  const icon = button.querySelector('svg');
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  let pointer = null;
  let startX = 0, startY = 0, dragged = false, suppressClick = false;
  let x = 0, y = 0, vx = 0, vy = 0, targetX = 0, targetY = 0;
  let frame = 0, lastTime = 0;

  function draw() { icon.style.transform = `translate(${x}px, ${y}px)`; }
  function animate(time) {
    const dt = Math.min((time - lastTime) / 1000 || 1 / 60, 1 / 30);
    lastTime = time;
    vx += ((targetX - x) * 280 - vx * 26) * dt;
    vy += ((targetY - y) * 280 - vy * 26) * dt;
    x += vx * dt; y += vy * dt;
    draw();
    if (Math.abs(x - targetX) + Math.abs(y - targetY) + Math.abs(vx) + Math.abs(vy) > .02) {
      frame = requestAnimationFrame(animate);
    } else {
      x = targetX; y = targetY; vx = vy = 0; frame = 0; draw();
    }
  }
  function moveTo(nextX, nextY) {
    targetX = nextX; targetY = nextY;
    if (reducedMotion.matches) {
      cancelAnimationFrame(frame); frame = 0;
      x = nextX; y = nextY; vx = vy = 0; draw();
    } else if (!frame) {
      lastTime = performance.now(); frame = requestAnimationFrame(animate);
    }
  }
  button.addEventListener('pointerdown', event => {
    if (!event.isPrimary || event.button !== 0 || pointer !== null) return;
    pointer = event.pointerId; startX = event.clientX; startY = event.clientY;
    dragged = false; suppressClick = false;
    button.setPointerCapture(pointer);
  });
  button.addEventListener('pointermove', event => {
    if (event.pointerId !== pointer) return;
    const dx = event.clientX - startX, dy = event.clientY - startY;
    dragged ||= Math.hypot(dx, dy) > 6;
    moveTo(8 * Math.tanh(dx / 32), 8 * Math.tanh(dy / 32));
  });
  function release(event) {
    if (event.pointerId !== pointer) return;
    suppressClick = dragged && event.type === 'pointerup';
    pointer = null;
    moveTo(0, 0);
    // The pointer click follows pointerup in the same event task. Keyboard clicks remain available.
    setTimeout(() => { suppressClick = false; }, 0);
  }
  button.addEventListener('pointerup', release);
  button.addEventListener('pointercancel', release);
  button.addEventListener('lostpointercapture', release);
  button.addEventListener('click', event => {
    if (suppressClick && event.detail !== 0) {
      event.preventDefault(); event.stopImmediatePropagation(); suppressClick = false;
    }
  }, true);
  button.addEventListener('contextmenu', event => event.preventDefault());
}

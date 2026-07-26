/* RetroFrame site — the live frame demo, reveals, and copy buttons.
   No dependencies. Everything degrades to a static page if this never runs. */

(() => {
  'use strict';

  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
  const fine = window.matchMedia('(hover: hover) and (pointer: fine)');

  /* ── the frame ─────────────────────────────────────────────────────────
     Mirrors the app: photos cross-fade on an interval, the clock shows the
     real time, and the controls stay hidden until you touch the screen and
     then hide themselves again after three seconds. */

  const screen = document.getElementById('screen');
  const stack = document.getElementById('stack');
  const controls = document.getElementById('controls');
  const hit = document.getElementById('hit');
  const timeEl = document.getElementById('demoTime');
  const dateEl = document.getElementById('demoDate');

  if (screen && stack) {
    const slides = Array.from(stack.querySelectorAll('img'));
    const INTERVAL = 4500;
    const HIDE_AFTER = 3000;

    let index = 0;
    let playing = !reduced.matches;
    let timer = null;
    let hideTimer = null;

    const show = (next) => {
      index = (next + slides.length) % slides.length;
      slides.forEach((img, i) => img.classList.toggle('is-on', i === index));
      // Nudge the next image into the browser cache before it's needed.
      const upcoming = slides[(index + 1) % slides.length];
      if (upcoming && !upcoming.complete) upcoming.loading = 'eager';
    };

    const start = () => {
      stop();
      if (!playing) return;
      timer = setInterval(() => show(index + 1), INTERVAL);
    };
    const stop = () => { if (timer) { clearInterval(timer); timer = null; } };

    /* Controls */
    const setControls = (on) => {
      controls.classList.toggle('is-on', on);
      controls.setAttribute('aria-hidden', String(!on));
      hit.setAttribute('aria-label', on ? 'Hide slideshow controls' : 'Show slideshow controls');
      clearTimeout(hideTimer);
      if (on) hideTimer = setTimeout(() => setControls(false), HIDE_AFTER);
    };

    const bump = () => { if (controls.classList.contains('is-on')) setControls(true); };

    hit.addEventListener('click', () => setControls(!controls.classList.contains('is-on')));

    controls.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-act]');
      if (!btn) return;

      switch (btn.dataset.act) {
        case 'prev': show(index - 1); start(); break;
        case 'next': show(index + 1); start(); break;
        case 'play':
          playing = !playing;
          btn.textContent = playing ? '❚❚' : '▶';
          btn.setAttribute('aria-label', playing ? 'Pause slideshow' : 'Play slideshow');
          playing ? start() : stop();
          break;
        case 'fav': {
          const on = btn.getAttribute('aria-pressed') !== 'true';
          btn.setAttribute('aria-pressed', String(on));
          btn.classList.remove('is-pop');
          void btn.offsetWidth;          // restart the animation
          btn.classList.add('is-pop');
          break;
        }
      }
      bump();
    });

    /* Real clock — the app shows the actual time, so the demo does too. */
    const tick = () => {
      const now = new Date();
      timeEl.textContent = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      dateEl.textContent = now.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
    };
    tick();
    setInterval(tick, 10000);

    /* Power-on, then run */
    if (!reduced.matches) {
      screen.classList.add('is-booting');
      screen.addEventListener('animationend', () => screen.classList.remove('is-booting'), { once: true });
    }
    start();

    /* Don't burn cycles while the tab is hidden or the frame is off screen. */
    document.addEventListener('visibilitychange', () => {
      document.hidden ? stop() : start();
    });

    if ('IntersectionObserver' in window) {
      new IntersectionObserver(([entry]) => {
        entry.isIntersecting ? start() : stop();
      }, { threshold: 0.15 }).observe(screen);
    }

    /* A slight tilt toward the pointer — the frame is a physical object on a
       shelf, so it should acknowledge where you're standing. Pointer devices
       only, and never when reduced motion is asked for. */
    const tablet = document.getElementById('tablet');
    const stage = document.getElementById('stage');
    if (tablet && stage && fine.matches && !reduced.matches && window.innerWidth >= 940) {
      let raf = null;
      stage.addEventListener('pointermove', (e) => {
        if (raf) return;
        raf = requestAnimationFrame(() => {
          raf = null;
          const r = stage.getBoundingClientRect();
          const x = (e.clientX - r.left) / r.width - 0.5;
          const y = (e.clientY - r.top) / r.height - 0.5;
          tablet.style.transform =
            `rotateY(${-7 + x * 7}deg) rotateX(${2 - y * 5}deg)`;
        });
      });
      stage.addEventListener('pointerleave', () => {
        tablet.style.transform = '';
      });
    }
  }

  /* ── scroll reveals ────────────────────────────────────────────────────── */

  const revealables = document.querySelectorAll('.reveal');
  if (!('IntersectionObserver' in window) || reduced.matches) {
    revealables.forEach((el) => el.classList.add('is-in'));
  } else {
    const io = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.style.setProperty('--d', `${entry.target.dataset.revealDelay || 0}ms`);
        entry.target.classList.add('is-in');
        io.unobserve(entry.target);
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.08 });
    revealables.forEach((el) => io.observe(el));
  }

  /* ── copy buttons ──────────────────────────────────────────────────────── */

  document.querySelectorAll('.copy').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const src = document.querySelector(btn.dataset.copy);
      if (!src) return;
      try {
        await navigator.clipboard.writeText(src.textContent.trim());
        const was = btn.textContent;
        btn.textContent = 'Copied';
        btn.classList.add('is-done');
        setTimeout(() => { btn.textContent = was; btn.classList.remove('is-done'); }, 1600);
      } catch {
        // Clipboard blocked (insecure context, denied permission) — select it
        // instead so the copy is still one keystroke away.
        const range = document.createRange();
        range.selectNodeContents(src);
        const sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
      }
    });
  });
})();

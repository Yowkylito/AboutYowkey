package dev.yowkey.portfolio.ui.sections

/*
 * On the web the static page shows first and plays the hero's entrance itself, in CSS written from the same timeline
 * (StaticHtml.heroMotion); the app then fades in over it. Starting the app's own entrance from the top there would
 * hide words the visitor is already reading and bring them in again, so the app joins the static page's entrance
 * where it has got to, which is usually its end: the still picture with every word shown.
 */

/**
 * How far into the hero's entrance the static page is, in milliseconds on [HeroMotion]'s timeline, as the app starts;
 * null where there is no static page (the desktop, Android), where it did not play the entrance (reduced motion, no
 * scripts), or where its hero was not what the visitor was shown (a link straight to a project).
 */
internal expect fun staticIntroMillis(): Int?

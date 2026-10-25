# AboutYowkey

The portfolio of **Rodelito Tangonan**, Android developer. It is built so that a recruiter who gives it seven seconds
still leaves knowing who I am, what I have shipped and how to reach me.

**Live:** https://yowkylito.github.io/AboutYowkey/

![The first screen: my name, my role and since when, three proof points, the contact buttons, and where I am](docs/images/hero.webp)

## What it is

One page, read from top to bottom, with a page of its own for each project. The site is also a work sample: it is
written entirely in Kotlin with Compose Multiplatform, the way I build Android apps, and the same code runs in the
browser, as a desktop window and as an app on my phone.

## The hierarchy

Each section is placed by what a hiring manager wants to know next. From the top:

1. **The first screen.** My name, my role and since when, my stack, and three numbers that prove them, each with
   the sentence behind it. Then **Email me** and **See projects**, with LinkedIn and GitHub beside them. The bar says
   where I am and that I am open to relocation. Nothing that matters sits below the fold, and the summary comes
   after the buttons so it can never push them down.
2. **Experience.** My roles, newest first, with what I did and what it changed.
3. **Projects.** Professional work first, then personal projects: four as cards, the rest as one-line rows. Each
   card answers three questions at a glance: what it looks like (a real screen of the app), what it is, and what it
   is built with and where it runs. A tap opens the project's own page, with its links right under its summary, then
   its highlights, how it is built, and its screens and clips.
4. **Skills.** Short and grouped, in the words job posts use. They come after the proof, not before it.
5. **Education & awards.** The degree and the awards, each with who gave it and when.
6. **Contact.** Where I am, the email (with a copy button) and profiles first. Below them, a private message form,
   then notes and star ratings left by colleagues and visitors.

## Highlights

- **Honest proof in the top bar.** While the first screen is up, the bar says where I am and that I am open to
  relocation. The visitors' average rating takes that place once there are five ratings, and the visit count once
  there are a thousand: a single 5.0 or a small count reads as weak proof. Once my name scrolls away, the bar shows
  my name and role instead.
- **A calm entrance.** The first screen reads in once, in order: name, role, numbers, buttons. When a portrait is
  set, it slides onto a stage in the site's blue first, with my name and role beside it.
- **Work told without secrets.** Code written for an employer belongs to the employer, so professional projects
  show my role, my share of the work and what it changed, never internal names or code.
- **Projects you can see.** Each card leads with a real screen of the app, not its splash. Each project page has
  60 fps clips, a full-screen viewer, and a link that can be shared directly.
- **Real social proof.** Visitors can leave a public note with a star rating. The page shows the average and how
  the stars spread once there are enough of them. Visits are counted without storing anyone's address.
- **A private line.** The message form reaches only me, and my phone app tells me about each message, each note and
  the day's numbers.
- **Readable by everyone.** A plain HTML copy of the page shows first, so search engines, link previews, screen
  readers and older browsers get all of it. There are light and dark themes, the motion follows the system's
  "reduce motion" setting, text contrast meets WCAG AA, and lines stay at a comfortable reading length.

## What I think matters in a portfolio

These ideas shaped every choice above:

- **Lead with proof, not adjectives.** "53% of the app's code, written by me" says more than "passionate
  developer". Every claim on the first screen is a number with the sentence behind it, in words a recruiter who
  doesn't write code can read.
- **Design for the skim.** The first screen answers who, what, where, why me and how to reach me. Everything after
  it is for the reader who wants more.
- **Order by what the reader weighs.** Experience and results come before side projects, skills after them, and
  the degree and awards last, because hiring managers weigh credentials least but recruiters still look for them.
- **Small numbers stay off the first screen.** A count that is still small argues against you, so live numbers show
  only once they are big enough.
- **Show, don't tell.** Real screens of real apps, and a site that is itself built with the skills it lists.
- **Respect confidentiality.** Describe your part and what it changed, never your employer's internals.
- **Make contact effortless.** The email button is the first button, and sending a message needs no account.
- **One touch of motion.** A single entrance, played once and never on reduced motion, gives the page polish
  without getting in the way of reading.
- **Keep the words apart from the code.** Every word on the site lives in one file. Editing it and pushing
  redeploys the site, and the build refuses to ship a broken page.

## Built with

Kotlin and Compose Multiplatform (WebAssembly in the browser, the JVM on the desktop, Android on the phone), a small
Cloudflare Worker for the visits, notes and messages, and GitHub Pages.

For developers: [editing the content, running, deploying, the backend and the phone app](docs/DEVELOPMENT.md),
[how the code is laid out](docs/ARCHITECTURE.md), and [what has been checked for production](docs/READINESS.md).

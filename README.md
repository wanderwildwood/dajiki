# 打字機 dajiki — Typewriter

A text editor that opens in landscape and stays there, for an E Ink phone with a Bluetooth
keyboard plugged into it. Built for the [Mudita Kompakt](https://mudita.com/products/kompakt/),
and it will install on any Android 12 device.

*Dajiki* is 打字機 — the character-striking machine. Nobody has said it in Japanese since the
war; タイプライター is the live word. The old one is here on purpose, for a machine that does
one thing slowly on a grey screen.

Not a fork. Written from scratch in Kotlin and Jetpack Compose, using Mudita's own
[MMD](https://github.com/mudita/MMD) design system so it looks like the apps the phone
already ships with.

## Why it exists

Because turning an E Ink phone into a writerdeck currently means one of two bad options.

The editors that force landscape do it with an invisible overlay window and a foreground
service, which works — it is the only thing that *does* work against an app whose manifest
declares portrait — but it costs a permanent notification and something running whenever you
are not writing. And the editors that are already on the phone are portrait-locked in their
own manifests, which no system setting anywhere can override.

An activity that declares its own orientation needs nothing running behind it. That is the
entire trick, and it is one line of a manifest. Everything else here is the text editor that
line needed to be attached to.

## What it does

- **Opens in landscape and stays there**, with nothing in the background holding it. Turned
  around, or following the device, if a keyboard case wants it the other way up. None of the
  three needs an accelerometer, which several of these panels do not have.
- **Keeps your writing as ordinary text files in a folder you choose**, through the system
  picker — so it can be a folder a sync app already owns, and the files are still there if
  this app is uninstalled. No storage permission is asked for, because that grant is not a
  permission.
- **Ctrl-S, Ctrl-N, Ctrl-O and Escape.** Everything else a keyboard does — Home, End, page
  keys, shift to select, word-wise arrows — is the platform's text field doing what it
  already does correctly, untouched.
- **Saves two seconds after you stop typing**, and again on the way out of the app. The foot
  of the page says when what you see is not yet on disk.
- **Counts the words**, a moment behind the typing rather than on every keystroke, because
  repainting the foot of an E Ink panel on every character is a flicker you would watch all
  afternoon.

## What it does not do

No permissions at all. No network — the one web address in the app is handed to a browser.
No formatting, no preview, no spellcheck, no themes, no sync. It does not rename a sheet: a
new one is named for the date and time it was started, and any file manager will rename it.

## Where this is up to

Version 0.1.0. The word count is unit tested and every screen has been driven, but **nobody
has written anything real in it yet** — no afternoon's work has gone through it, and that is
the test that matters for a writing app. The save path is the part to be suspicious of, and
it is the part that would cost the most.

The icon is a placeholder.

## Building

```
./gradlew assembleDebug
```

A release build needs a keystore at `signing/signing.keystore` with a matching
`signing/signing.properties`. There is no fallback key in this repository: without one, a
release build comes out unsigned rather than wrongly signed.

## Licence

GNU General Public License v3.0 only. Copyright wander wildwood.

Icons are from Material Symbols, Apache 2.0.

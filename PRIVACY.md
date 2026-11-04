# Privacy

Typewriter asks for nothing and sends nothing, because it has no way to send anything
anywhere.

That is the whole policy. The rest of this page is the evidence for it, because a privacy
policy that cannot be checked is just a promise.

## No permissions

`app/src/main/AndroidManifest.xml` declares none. There is no `android.permission.INTERNET`,
so the app cannot open a network connection even by accident: Android refuses it at the socket.

One entry does appear if you read the permissions out of the built APK rather than out of this
file, and it is worth naming rather than letting you find it:
`com.wanderwildwood.dajiki.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. It is not an Android
permission and it asks nothing of you. AndroidX defines it inside this app's own package, at
signature level, so that a broadcast receiver the app registers at runtime is not exposed to
other apps. Only something signed with the same key could hold it, which means only this app.
It is a lock, not a key.

The folder your writing lives in is not reached through a storage permission. You pick it
from the system's own folder picker, and the grant that comes back covers that folder and
nothing else — not the rest of your storage, and not any other folder.

## Where your writing is

In that folder, as the plain text files you can see there. Not in a database inside the app,
not in a cache, and not in a copy anywhere else. Uninstalling this app does not take them
with it.

## The one address in the app

The llama at the foot of the About opens `square.link/u/AGu8oT10` in whatever browser you
have. That is a hand-off: this app does not fetch the page, and it learns nothing about
whether you went there. The page is Square's, a separate company, and what it collects is
covered by Square's own privacy policy.

The same goes for a sync app you may point at your folder: copying your sheets elsewhere is
that app's doing, under its own policy, not this one's.

<!-- shared-about privacy section -->
## Who we are

Typewriter is made by wander wildwood, an independent developer: one person, not a company. wander wildwood is the data controller for anything the app itself handles, and that is very little. Typewriter keeps what you put in it on your phone and sends nothing to the developer, so the developer holds no data about you.

## How to contact us

Write to email@wanderthe.dev about your data, this policy or anything else. The same address is in the app, under About.

## Your rights

If you are in the EU, the EEA or the UK, the GDPR gives you the right to:

- see the personal data held about you (access)
- have it corrected (rectification) or deleted (erasure)
- limit how it is used (restriction)
- object to its use (objection)
- get a copy to take elsewhere (portability)
- withdraw your consent at any time, where something rests on it
- complain to a data protection supervisory authority, for example the one where you live

Similar laws elsewhere, such as the CCPA in California, give you the right to know, delete and correct, and to opt out of the sale or sharing of your data. Nothing from Typewriter is sold or shared.

Since Typewriter keeps what it stores only on your phone, you can exercise most of these rights yourself: delete a sheet in the app's Files list or its file in your folder, clear the app's data in Android's settings, or uninstall the app, which deletes what the app keeps (your sheets stay in their folder; see below). For anything you cannot do yourself, write to the address above.
<!-- /shared-about privacy section -->

Your sheets are a different matter from the app's own data: they are plain files in the folder
you chose, so clearing the app's data or uninstalling it leaves them where they are. To delete
them, delete the files.

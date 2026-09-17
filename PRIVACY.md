# Privacy

Typewriter asks for nothing and sends nothing, because it has no way to send anything
anywhere.

That is the whole policy. The rest of this page is the evidence for it, because a privacy
policy that cannot be checked is just a promise.

## No permissions

`app/src/main/AndroidManifest.xml` declares none. Not one. There is no
`android.permission.INTERNET`, so the app cannot open a network connection even by accident:
Android refuses it at the socket.

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
whether you went there.

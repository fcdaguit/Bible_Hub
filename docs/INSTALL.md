# Installation guide

## Install the APK
1. On GitHub, open **Actions → Build APK →** latest green run → download the **sermonprep-debug-apk** artifact (or the `.apk` from **Releases** if a version tag was pushed). Unzip it.
2. Copy the `.apk` to your phone (Android 8.0 or newer, 64-bit ARM).
3. Open it. Android will ask to allow installs from this source (your browser or file manager). Allow it, then install.

Debug builds are signed with a debug key, so you will see a "not from Play Store" warning. For a normal release signature, see `docs/BUILD.md`.

## First run
1. Open **Settings → Bible translations** and tap **Download** on at least one translation (needs internet once; roughly 4–5 MB each). The first one becomes your preferred translation.
2. Pick Light / Dark and your language in Settings if you want.
3. Open **Sermons**. Three samples are provided; tap one, then tap a highlighted reference (e.g. *John 3:16*) to see the verse window.

After step 1 everything works with no connection.

## Optional: offline AI
**If you already have a model on the phone** (for example Gemma 3n E4B in your Downloads folder):
1. Open **Settings → Offline AI model → Allow file access**. Android sends you to a system screen; switch on *All files access* for Sermon Prep and come back.
2. The app scans shared storage for `.task` and `.litertlm` files over 100 MB. If it finds exactly one and none is set up yet, it uses it automatically. Otherwise tap **Use** on the one you want.
3. The file is used where it is: not copied, and never deleted by the app.

A model that exists only inside another app's private storage (Google AI Edge Gallery keeps its downloads there) cannot be seen by any other app. Copy or download the file into a normal folder such as Downloads, or use **Import model file**.

**If you do not have a model yet:** download one yourself (Gemma needs a licence acceptance on the host site), then use the steps above or **Import model file** (copies it, several GB).

Then, in **Search**, turn on *Use offline AI*, or open **Assistant**. Models need plenty of free RAM; if the app closes while loading, try a smaller model.

## Your own Bible file
**Settings → Import from file…** accepts:
- CSV with header `Book,Chapter,Verse,Text`
- JSON array of 66 books, each `{"chapters": [["verse 1", "verse 2"], ...]}`
- text lines `book|chapter|verse|text` (book as 1–66 or a name)

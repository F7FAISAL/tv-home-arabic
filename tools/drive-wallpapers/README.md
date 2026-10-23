# Drive wallpapers: the Apps Script (optional)

Lists the image files of one Google Drive folder as JSON for the launcher (RemoteWallpapers, source DRIVE).
Read-only (`drive.readonly`), one folder fixed in the code, every request parameter ignored. Each user deploys
their own copy for their own folder; there is no shared script and no key in this repository.

1. Make a folder in Google Drive and put your photos in it (JPEG, PNG, WebP or HEIC; up to 60 are used).
2. script.google.com → New project. Paste `Code.gs`; set `FOLDER_ID` to your folder's id (the part of the folder
   link after `/folders/`).
3. Project Settings → tick "Show appsscript.json manifest file in editor"; paste `appsscript.json`.
4. Deploy → New deployment → Web app: Execute as **Me**, Who has access **Anyone** → Deploy → Allow
   ("Google hasn't verified this app": Advanced → Go to the project; it is your own script).
5. Open the `/exec` link once in a browser: you should see `{"files":[...]}` with your photos.
6. Put the link in your theme's `config.local.json` (gitignored, never committed):
   `{ "wallpaper_list_url": "https://script.google.com/macros/s/.../exec" }`, then push the theme with
   `tools/theme.sh`. The TV syncs at boot and every six hours; photos join the gallery and bundled ones.

Anyone with the link can read the folder's file list (names and sizes); keep it private. To stop, delete the
deployment in Apps Script and remove the line from `config.local.json`.

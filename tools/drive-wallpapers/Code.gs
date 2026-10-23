/**
 * TV wallpapers: lists the image files of ONE Google Drive folder as JSON, for the cleanyourtv launcher.
 * Read-only (manifest scope drive.readonly). It lists only FOLDER_ID: no other folder, no subfolders, no
 * file contents, and it ignores every request parameter. Deploy as a web app (see README.md).
 */
// Your folder's id: the part of its Drive link after /folders/.
const FOLDER_ID = 'PASTE_YOUR_FOLDER_ID_HERE';
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif'];
const MAX_FILES = 200;

function doGet() {
  const folder = DriveApp.getFolderById(FOLDER_ID);
  const files = folder.getFiles();
  const out = [];
  while (files.hasNext() && out.length < MAX_FILES) {
    const f = files.next();
    if (f.isTrashed() || IMAGE_TYPES.indexOf(f.getMimeType()) < 0) continue;
    out.push({
      id: f.getId(),
      name: f.getName(),
      modified: f.getLastUpdated().toISOString(),
      size: f.getSize()
    });
  }
  out.sort(function (a, b) { return a.name.localeCompare(b.name); });
  return ContentService
      .createTextOutput(JSON.stringify({ files: out }))
      .setMimeType(ContentService.MimeType.JSON);
}

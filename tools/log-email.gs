// Google Apps Script — Auto Answer log email relay
//
// One-time setup:
//   1. Sign in to https://script.google.com with the Gmail account you want
//      the log emails delivered to (e.g. simonjknowles@gmail.com).
//   2. Click "+ New project".
//   3. Replace any existing code in the editor with this entire file's contents.
//   4. Change RECIPIENT below to your destination email address.
//   5. Click "Deploy" (top right) -> "New deployment".
//      - Select type: "Web app"
//      - Description: "Auto Answer log email"
//      - Execute as: "Me (you@gmail.com)"
//      - Who has access: "Anyone"
//   6. Click "Deploy" -> the first time, Google will ask for permission to
//      send mail on your behalf. Approve.
//   7. Copy the "Web app URL" it shows (looks like
//      https://script.google.com/macros/s/AKfycb.../exec).
//   8. In the Auto Answer app, paste that URL into the "Apps Script web app
//      URL" field and toggle "Daily log email" on.
//   9. Tap "Send test email now" -> you should receive a Gmail within 30 sec.
//
// Cost: free, no quotas you will hit at one email per day. Uses your own
// Gmail account, so no third-party API key sits in the app.

const RECIPIENT = 'simonjknowles@gmail.com';

function doPost(e) {
  try {
    const params = JSON.parse(e.postData.contents);
    const subject = params.subject || 'Auto Answer log';
    const body = params.body || '(empty body)';
    MailApp.sendEmail({
      to: RECIPIENT,
      subject: subject,
      body: body,
      name: 'Auto Answer (tablet)',
    });
    return ContentService.createTextOutput('ok')
      .setMimeType(ContentService.MimeType.TEXT);
  } catch (err) {
    return ContentService.createTextOutput('error: ' + err.message)
      .setMimeType(ContentService.MimeType.TEXT);
  }
}

function doGet() {
  return ContentService.createTextOutput(
    'Auto Answer log email relay is live. POST JSON {subject, body} to send.'
  ).setMimeType(ContentService.MimeType.TEXT);
}

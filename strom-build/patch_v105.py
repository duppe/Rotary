from pathlib import Path
import sys
root=Path(sys.argv[1])

# MainActivity
p=root/'app/src/main/java/no/haavald/streamsaver/MainActivity.java'
s=p.read_text()
s=s.replace('private static final int PICK_FOLDER = 400;', 'private static final int PICK_FOLDER = 400, PICK_STREAM = 402;')
s=s.replace('private Button startButton, stopButton, cancelButton, folderButton, pasteButton, exampleButton, clearHistoryButton;', 'private Button startButton, stopButton, cancelButton, folderButton, pasteButton, exampleButton, browserButton, clearHistoryButton;')
s=s.replace('private String tree = "", historySignature = "";', 'private String tree = "", historySignature = "";\n    private String selectedUserAgent = "", selectedReferer = "", selectedCookie = "", selectedOrigin = "";')
s=s.replace('''        marginAdd(input, inputButtons, 8);
        pasteButton.setOnClickListener(v -> paste());
        exampleButton.setOnClickListener(v -> new AlertDialog.Builder(this)''','''        marginAdd(input, inputButtons, 8);
        browserButton = button("Åpne nettside og velg strøm", false);
        marginAdd(input, browserButton, 8);
        browserButton.setOnClickListener(v -> openMediaBrowser());
        pasteButton.setOnClickListener(v -> paste());
        exampleButton.setOnClickListener(v -> new AlertDialog.Builder(this)''')
s=s.replace('new AlertDialog.Builder(this).setTitle("StrømLagrer 1.0.4")','new AlertDialog.Builder(this).setTitle("StrømLagrer 1.0.5")')
s=s.replace('Andre nettsider blir ikke automatisk analysert.','For andre nettsider kan du bruke «Åpne nettside og velg strøm». Start videoen i den innebygde nettleseren og velg en oppdaget strøm.')
s=s.replace('TextView footer = text("By Duppe · v1.0.4", 12, MUTED, false);','TextView footer = text("By Duppe · v1.0.5", 12, MUTED, false);')
s=s.replace('''    private void chooseFolder() {''','''    private void openMediaBrowser() {
        String url = extractUrl(urlField.getText().toString());
        try {
            URI parsed = new URI(url);
            if ((!("https".equalsIgnoreCase(parsed.getScheme()) || "http".equalsIgnoreCase(parsed.getScheme()))) || parsed.getHost() == null)
                throw new IllegalArgumentException();
        } catch (Exception e) {
            urlField.setError("Skriv inn en gyldig nettsideadresse først.");
            return;
        }
        Intent intent = new Intent(this, BrowserActivity.class).putExtra("url", url);
        try { startActivityForResult(intent, PICK_STREAM); }
        catch (Exception e) { showError("Kunne ikke starte den innebygde nettleseren. Kontroller at Android System WebView er aktivert."); }
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private void chooseFolder() {''')
s=s.replace('''    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri chosen = data.getData();''','''    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_STREAM) {
            if (resultCode == RESULT_OK && data != null) {
                String media = data.getStringExtra("media_url");
                if (media != null && !media.trim().isEmpty()) {
                    urlField.setText(media);
                    selectedUserAgent = safe(data.getStringExtra("user_agent"));
                    selectedReferer = safe(data.getStringExtra("referer"));
                    selectedCookie = safe(data.getStringExtra("cookie"));
                    selectedOrigin = safe(data.getStringExtra("origin"));
                    toast("Strøm valgt. Velg lagringsmappe og trykk Last ned / ta opp.");
                }
            }
            return;
        }
        if (requestCode != PICK_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri chosen = data.getData();''')
s=s.replace('''.putExtra("best", quality.getSelectedItemPosition() == 0)
                .putExtra("duration", LIMITS[duration.getSelectedItemPosition()] * 60000L);''','''.putExtra("best", quality.getSelectedItemPosition() == 0)
                .putExtra("duration", LIMITS[duration.getSelectedItemPosition()] * 60000L)
                .putExtra("user_agent", selectedUserAgent)
                .putExtra("referer", selectedReferer)
                .putExtra("cookie", selectedCookie)
                .putExtra("origin", selectedOrigin);''')
s=s.replace('pasteButton.setEnabled(!active); exampleButton.setEnabled(!active);','pasteButton.setEnabled(!active); exampleButton.setEnabled(!active); browserButton.setEnabled(!active);')
p.write_text(s)

# DownloadService
p=root/'app/src/main/java/no/haavald/streamsaver/DownloadService.java'
s=p.read_text()
s=s.replace('''        final boolean best = intent.getBooleanExtra("best", true);
        final long duration = Math.max(1000L, Math.min(intent.getLongExtra("duration", 350L * 60000L), 350L * 60000L));
        worker = new Thread(() -> execute(inputUrl, tree, requestedName, best, duration), "stream-download");''','''        final boolean best = intent.getBooleanExtra("best", true);
        final long duration = Math.max(1000L, Math.min(intent.getLongExtra("duration", 350L * 60000L), 350L * 60000L));
        final StreamDownloader.RequestContext requestContext = new StreamDownloader.RequestContext(
                intent.getStringExtra("user_agent"), intent.getStringExtra("referer"),
                intent.getStringExtra("cookie"), intent.getStringExtra("origin"));
        worker = new Thread(() -> execute(inputUrl, tree, requestedName, best, duration, requestContext), "stream-download");''')
s=s.replace('private void execute(String inputUrl, String tree, String requestedName, boolean best, long duration) {','private void execute(String inputUrl, String tree, String requestedName, boolean best, long duration, StreamDownloader.RequestContext requestContext) {')
s=s.replace('downloader = new StreamDownloader(new SafStorage(getContentResolver(), Uri.parse(tree)), this::onProgress, temp);','downloader = new StreamDownloader(new SafStorage(getContentResolver(), Uri.parse(tree)), this::onProgress, temp, requestContext);')
p.write_text(s)

# StreamDownloader
p=root/'app/src/main/java/no/haavald/streamsaver/core/StreamDownloader.java'
s=p.read_text()
s=s.replace('''    public interface Listener {
        void onProgress(Progress p);
    }
''','''    public interface Listener {
        void onProgress(Progress p);
    }

    public static final class RequestContext {
        public final String userAgent, referer, cookie, origin;
        public RequestContext(String userAgent, String referer, String cookie, String origin) {
            this.userAgent = cleanHeader(userAgent);
            this.referer = cleanHeader(referer);
            this.cookie = cleanHeader(cookie);
            this.origin = cleanHeader(origin);
        }
        public static RequestContext empty() { return new RequestContext("", "", "", ""); }
        private static String cleanHeader(String value) {
            if (value == null) return "";
            return value.replace("\\r", "").replace("\\n", "").trim();
        }
    }
''')
s=s.replace('private final File cacheDir;','private final File cacheDir;\n    private final RequestContext requestContext;')
s=s.replace('''    public StreamDownloader(Storage storage, Listener listener, File cacheDir) {
        if (storage == null || listener == null || cacheDir == null) {
            throw new IllegalArgumentException("Storage, listener and cacheDir are required");
        }
        this.storage = storage;
        this.listener = listener;
        this.cacheDir = cacheDir;
    }''','''    public StreamDownloader(Storage storage, Listener listener, File cacheDir) {
        this(storage, listener, cacheDir, RequestContext.empty());
    }

    public StreamDownloader(Storage storage, Listener listener, File cacheDir, RequestContext requestContext) {
        if (storage == null || listener == null || cacheDir == null) {
            throw new IllegalArgumentException("Storage, listener and cacheDir are required");
        }
        this.storage = storage;
        this.listener = listener;
        this.cacheDir = cacheDir;
        this.requestContext = requestContext == null ? RequestContext.empty() : requestContext;
    }''')
s=s.replace('''                connection.setRequestProperty("User-Agent", "StreamSaver/1.0 (Android)");
                connection.setRequestProperty("Accept", "*/*");''','''                connection.setRequestProperty("User-Agent", requestContext.userAgent.isEmpty() ? "StreamSaver/1.0 (Android)" : requestContext.userAgent);
                if (!requestContext.referer.isEmpty()) connection.setRequestProperty("Referer", requestContext.referer);
                if (!requestContext.cookie.isEmpty()) connection.setRequestProperty("Cookie", requestContext.cookie);
                if (!requestContext.origin.isEmpty()) connection.setRequestProperty("Origin", requestContext.origin);
                connection.setRequestProperty("Accept", "*/*");''')
p.write_text(s)

# Manifest
p=root/'app/src/main/AndroidManifest.xml'
s=p.read_text()
s=s.replace('''        <activity android:name=".MainActivity" android:exported="true"''','''        <activity android:name=".BrowserActivity" android:exported="false"
            android:configChanges="keyboardHidden|orientation|screenSize" />
        <activity android:name=".MainActivity" android:exported="true"''')
p.write_text(s)

# Version
p=root/'app/build.gradle'
s=p.read_text().replace('versionCode 5','versionCode 6').replace("versionName '1.0.4'","versionName '1.0.5'")
p.write_text(s)

# sanity
assert 'Åpne nettside og velg strøm' in (root/'app/src/main/java/no/haavald/streamsaver/MainActivity.java').read_text()
assert 'RequestContext' in (root/'app/src/main/java/no/haavald/streamsaver/core/StreamDownloader.java').read_text()

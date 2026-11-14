package no.haavald.streamsaver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BrowserActivity extends Activity {
    private static final int BG=Color.rgb(11,18,32), PANEL=Color.rgb(20,31,48), TEXT=Color.rgb(239,245,253), MUTED=Color.rgb(159,177,199), ACCENT=Color.rgb(83,198,232), LINE=Color.rgb(42,60,81);
    private WebView webView;
    private TextView status;
    private final LinkedHashMap<String,MediaHit> hits=new LinkedHashMap<>();
    private String pageUrl="", userAgent="";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        buildUi();
        String start=getIntent().getStringExtra("url");
        if(start==null||start.trim().isEmpty()){finish();return;}
        configureWebView();
        pageUrl=start;
        webView.loadUrl(start);
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(12),dp(12),dp(12),dp(12));
        root.addView(text("Finn strøm på nettsiden",20,TEXT,true),new LinearLayout.LayoutParams(-1,-2));
        status=text("Start videoen på siden. Appen følger med på medieforespørsler.",13,MUTED,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(6); root.addView(status,sp);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button choose=button("Velg strøm",true), refresh=button("Søk på nytt",false);
        LinearLayout.LayoutParams a1=new LinearLayout.LayoutParams(0,dp(50),1f), a2=new LinearLayout.LayoutParams(0,dp(50),1f); a2.leftMargin=dp(8);
        actions.addView(choose,a1); actions.addView(refresh,a2);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2); ap.topMargin=dp(10); root.addView(actions,ap);
        webView=new WebView(this);
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,0,1f); wp.topMargin=dp(10); root.addView(webView,wp);
        setContentView(root);
        choose.setOnClickListener(v->chooseSource());
        refresh.setOnClickListener(v->{inspectMediaElements();webView.reload();Toast.makeText(this,"Søker etter strømmer på nytt …",Toast.LENGTH_SHORT).show();});
    }

    private void configureWebView(){
        WebSettings s=webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        userAgent=s.getUserAgentString();
        webView.addJavascriptInterface(new MediaBridge(),"StreamSaverBridge");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){pageUrl=url==null?pageUrl:url;inspectMediaElements();updateStatus();}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                try{
                    Uri u=request.getUrl(); String url=u==null?null:u.toString(); Map<String,String> headers=request.getRequestHeaders();
                    if(looksLikeMedia(url,header(headers,"Accept"),header(headers,"Sec-Fetch-Dest"))) observe(url,headers,false);
                }catch(Throwable ignored){}
                return super.shouldInterceptRequest(view,request);
            }
            @Override public void onLoadResource(WebView view,String url){if(looksLikeMedia(url,"",""))observe(url,null,false);super.onLoadResource(view,url);}
        });
    }

    private void inspectMediaElements(){
        if(webView==null)return;
        String js="(function(){var a=[];function p(x){if(x&&a.indexOf(x)<0)a.push(x);}document.querySelectorAll('video,audio').forEach(function(e){p(e.currentSrc);p(e.src);e.querySelectorAll('source').forEach(function(s){p(s.src);});});a.forEach(function(x){StreamSaverBridge.media(x);});})();";
        try{webView.evaluateJavascript(js,null);}catch(Throwable ignored){}
    }

    private void observe(String url,Map<String,String> headers,boolean fromElement){
        if(url==null||url.isEmpty()||url.startsWith("blob:")||url.startsWith("data:"))return;
        if(!url.startsWith("http://")&&!url.startsWith("https://"))return;
        synchronized(hits){
            MediaHit old=hits.get(url);
            if(old==null){old=new MediaHit(url);hits.put(url,old);while(hits.size()>120){String first=hits.keySet().iterator().next();hits.remove(first);}}
            old.fromElement|=fromElement;
            if(headers!=null){
                String ua=header(headers,"User-Agent"); if(!ua.isEmpty())old.userAgent=ua;
                String ref=header(headers,"Referer"); if(!ref.isEmpty())old.referer=ref;
                String origin=header(headers,"Origin"); if(!origin.isEmpty())old.origin=origin;
            }
        }
        runOnUiThread(this::updateStatus);
    }

    private void updateStatus(){
        int count; synchronized(hits){count=filtered().size();}
        status.setText(count==0?"Ingen strøm funnet ennå. Start avspillingen på siden, og trykk eventuelt Søk på nytt.":count+(count==1?" mulig strøm funnet. Trykk Velg strøm.":" mulige strømmer funnet. Trykk Velg strøm."));
    }

    private void chooseSource(){
        inspectMediaElements();
        List<MediaHit> list; synchronized(hits){list=filtered();}
        if(list.isEmpty()){new AlertDialog.Builder(this).setTitle("Ingen strøm funnet").setMessage("Start videoen på siden først. Hvis videoen allerede spiller, trykk Søk på nytt og prøv Velg strøm igjen.").setPositiveButton("OK",null).show();return;}
        String[] labels=new String[list.size()]; for(int i=0;i<list.size();i++)labels[i]=label(list.get(i));
        new AlertDialog.Builder(this).setTitle("Velg strøm").setItems(labels,(d,which)->returnSelection(list.get(which))).setNegativeButton("Avbryt",null).show();
    }

    private List<MediaHit> filtered(){
        ArrayList<MediaHit> all=new ArrayList<>(hits.values());
        all.removeIf(h->!isSelectable(h.url));
        all.sort((a,b)->Integer.compare(score(b.url),score(a.url)));
        return all;
    }

    private static boolean isSelectable(String url){
        String u=url.toLowerCase(Locale.ROOT);
        if(u.contains(".m3u8")||u.contains(".mp4")||u.contains(".m4v")||u.contains(".mov")||u.contains(".webm")||u.contains(".mkv")||u.contains(".mp3")||u.contains(".m4a")||u.contains(".aac")||u.contains(".ogg")||u.contains(".opus")||u.contains(".flac")||u.contains(".ts")||u.contains("mime=video")||u.contains("mime=audio"))return true;
        return u.contains("video")||u.contains("media")||u.contains("stream");
    }

    private static int score(String url){
        String u=url.toLowerCase(Locale.ROOT); int s=0;
        if(u.contains(".m3u8"))s+=1000;
        if(u.matches(".*\\.(mp4|m4v|mov|webm|mkv)(\\?|$).*"))s+=900;
        if(u.matches(".*\\.(mp3|m4a|aac|ogg|opus|flac)(\\?|$).*"))s+=800;
        if(u.matches(".*\\.ts(\\?|$).*"))s+=300;
        if(u.contains("master"))s+=120; if(u.contains("playlist"))s+=80;
        if(u.matches(".*(segment|frag|chunk|part)[-_]?\\d+.*"))s-=250;
        return s;
    }

    private String label(MediaHit h){
        String u=h.url, low=u.toLowerCase(Locale.ROOT);
        String type=low.contains(".m3u8")?"HLS / m3u8":low.matches(".*\\.ts(\\?|$).*")?"TS":low.matches(".*\\.(mp4|m4v|mov|webm|mkv)(\\?|$).*")?"Video":"Mediestrøm";
        String host=""; try{host=new URI(u).getHost();}catch(Exception ignored){}
        String shortUrl=u.length()>90?u.substring(0,87)+"…":u;
        return type+(host==null||host.isEmpty()?"":" · "+host)+"\n"+shortUrl;
    }

    private void returnSelection(MediaHit h){
        String ref=!h.referer.isEmpty()?h.referer:pageUrl, ua=!h.userAgent.isEmpty()?h.userAgent:userAgent, cookie="";
        try{cookie=CookieManager.getInstance().getCookie(h.url);}catch(Throwable ignored){}
        Intent out=new Intent();
        out.putExtra("media_url",h.url);
        out.putExtra("user_agent",ua==null?"":ua);
        out.putExtra("referer",ref==null?"":ref);
        out.putExtra("cookie",cookie==null?"":cookie);
        out.putExtra("origin",!h.origin.isEmpty()?h.origin:originOf(ref));
        setResult(RESULT_OK,out); finish();
    }

    private static String originOf(String url){
        try{URI u=new URI(url);if(u.getScheme()==null||u.getHost()==null)return "";int p=u.getPort();return u.getScheme()+"://"+u.getHost()+(p>0?":"+p:"");}catch(Exception e){return "";}
    }
    private static String header(Map<String,String> map,String key){if(map==null)return "";for(Map.Entry<String,String> e:map.entrySet())if(key.equalsIgnoreCase(e.getKey()))return e.getValue()==null?"":e.getValue();return "";}
    private static boolean looksLikeMedia(String url,String accept,String dest){
        if(url==null)return false; String u=url.toLowerCase(Locale.ROOT);
        if(u.startsWith("blob:")||u.startsWith("data:"))return false;
        if(u.contains(".m3u8")||u.matches(".*\\.(mp4|m4v|mov|webm|mkv|ts|m2ts|mp3|aac|m4a|ogg|opus|flac)(\\?|$).*"))return true;
        String a=accept==null?"":accept.toLowerCase(Locale.ROOT), d=dest==null?"":dest.toLowerCase(Locale.ROOT);
        return a.contains("video/")||a.contains("audio/")||"video".equals(d)||"audio".equals(d);
    }

    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(webView!=null){try{webView.removeJavascriptInterface("StreamSaverBridge");webView.stopLoading();webView.destroy();}catch(Throwable ignored){}webView=null;}super.onDestroy();}
    private final class MediaBridge{@JavascriptInterface public void media(String url){observe(url,null,true);}}
    private static final class MediaHit{final String url;String userAgent="",referer="",origin="";boolean fromElement;MediaHit(String url){this.url=url;}}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1f);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    private Button button(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(primary?16:14);b.setTextColor(primary?BG:TEXT);b.setBackground(background(primary?ACCENT:PANEL,primary?0:LINE));return b;}
    private GradientDrawable background(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
}
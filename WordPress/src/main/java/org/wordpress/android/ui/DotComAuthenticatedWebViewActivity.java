package org.wordpress.android.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.WordPressDB;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.UrlUtils;
import org.wordpress.android.util.WPWebChromeClient;
import org.wordpress.passcodelock.AppLockManager;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Activity for opening external links in a webview being authenticated to WordPress.com.
 */
public class DotComAuthenticatedWebViewActivity extends WebViewActivity {
    public static final String AUTHENTICATION_URL = "authenticated_url";
    public static final String AUTHENTICATION_USER = "authenticated_user";
    public static final String AUTHENTICATION_PASSWD = "authenticated_passwd";
    public static final String URL_TO_LOAD = "url_to_load";
    public static final String WPCOM_LOGIN_URL = "https://wordpress.com/wp-login.php";

    public static void openUrlByUsingMainWPCOMCredentials(Context context, String url) {
        if (context == null) {
            AppLog.e(AppLog.T.UTILS, "Context is null in openUrlByUsingMainWPCOMCredentials!");
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String authenticatedUser = settings.getString(WordPress.WPCOM_USERNAME_PREFERENCE, null);
        String authenticatedPassword = WordPressDB.decryptPassword(
                settings.getString(WordPress.WPCOM_PASSWORD_PREFERENCE, null)
        );

        openURL(context, url, authenticatedUser, authenticatedPassword);
    }

    public static void openUrlByUsingWPCOMCredentials(Context context, String url, String user, String password) {
        openURL(context, url, user, password);
    }

    private static void openURL(Context context, String url, String user, String password) {
        if (context == null) {
            AppLog.e(AppLog.T.UTILS, "Context is null!!!");
            return;
        }

        if (TextUtils.isEmpty(url)) {
            AppLog.e(AppLog.T.UTILS, "Empty or null URL passed to openUrlByUsingMainWPCOMCredentials!!");
            Toast.makeText(context, context.getResources().getText(R.string.invalid_url_message),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(user) || TextUtils.isEmpty(password)) {
            AppLog.e(AppLog.T.UTILS, "Username and/or password empty/null!!!");
            return;
        }

        Intent intent = new Intent(context, DotComAuthenticatedWebViewActivity.class);
        intent.putExtra(DotComAuthenticatedWebViewActivity.AUTHENTICATION_USER, user);
        intent.putExtra(DotComAuthenticatedWebViewActivity.AUTHENTICATION_PASSWD, password);
        intent.putExtra(DotComAuthenticatedWebViewActivity.URL_TO_LOAD, url);
        intent.putExtra(DotComAuthenticatedWebViewActivity.AUTHENTICATION_URL, WPCOM_LOGIN_URL);
        context.startActivity(intent);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();

        mWebView.setWebViewClient(new WebViewClient());
        mWebView.setWebChromeClient(new WPWebChromeClient(this, (ProgressBar) findViewById(R.id.progress_bar)));
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setUserAgentString(WordPress.getUserAgent());
        mWebView.getSettings().setDomStorageEnabled(true);

        if (extras != null) {
            String addressToLoad = extras.getString(URL_TO_LOAD);
            String username = extras.getString(AUTHENTICATION_USER, "");
            String password = extras.getString(AUTHENTICATION_PASSWD, "");
            String authURL = extras.getString(AUTHENTICATION_URL);

            if (TextUtils.isEmpty(addressToLoad) || !UrlUtils.isValidUrlAndHostNotNull(addressToLoad)) {
                AppLog.e(AppLog.T.UTILS, "Empty or null or invalid URL passed to DotComAuthenticatedWebViewActivity!!");
                Toast.makeText(this, getResources().getText(R.string.invalid_url_message),
                        Toast.LENGTH_SHORT).show();
                finish();
            }

            if (TextUtils.isEmpty(authURL) || !UrlUtils.isValidUrlAndHostNotNull(authURL)) {
                AppLog.e(AppLog.T.UTILS, "Empty or null or invalid auth URL passed to DotComAuthenticatedWebViewActivity!!");
                Toast.makeText(this, getResources().getText(R.string.invalid_url_message),
                        Toast.LENGTH_SHORT).show();
                finish();
            }

            if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
                AppLog.e(AppLog.T.UTILS, "Username and/or password empty/null!!!");
                Toast.makeText(this, getResources().getText(R.string.incorrect_credentials),
                        Toast.LENGTH_SHORT).show();
                finish();
            }

            this.loadAuthenticatedUrl(authURL, addressToLoad, username, password);
        } else {
            AppLog.e(AppLog.T.UTILS, "No valid parameters passed to DotComAuthenticatedWebViewActivity!!");
            finish();
        }
    }

    /**
     * Login to the WordPress.com and load the specified URL.
     *
     */
    protected void loadAuthenticatedUrl(String authenticationURL, String urlToLoad, String username, String passwd) {
        try {
            String postData = String.format("log=%s&pwd=%s&redirect_to=%s",
                    URLEncoder.encode(username, "UTF-8"), URLEncoder.encode(passwd, "UTF-8"),
                    URLEncoder.encode(urlToLoad, "UTF-8"));
            mWebView.postUrl(authenticationURL, postData.getBytes());
        } catch (UnsupportedEncodingException e) {
            AppLog.e(AppLog.T.UTILS, e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.webview, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (mWebView == null) {
            return false;
        }

        int itemID = item.getItemId();
        if (itemID == R.id.menu_refresh) {
            mWebView.reload();
            return true;
        } else if (itemID == R.id.menu_share) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, mWebView.getUrl());
            startActivity(Intent.createChooser(share, getResources().getText(R.string.share_link)));
            return true;
        } else if (itemID == R.id.menu_browser) {
            String url = mWebView.getUrl();
            if (url != null) {
                Uri uri = Uri.parse(url);
                if (uri != null) {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(uri);
                    startActivity(i);
                    AppLockManager.getInstance().setExtendedTimeout();
                }
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

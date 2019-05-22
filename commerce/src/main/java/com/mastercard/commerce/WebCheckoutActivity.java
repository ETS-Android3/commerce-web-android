/* Copyright © 2019 Mastercard. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 =============================================================================*/

package com.mastercard.commerce;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.net.URI;
import java.net.URISyntaxException;

import static com.mastercard.commerce.CommerceWebSdk.COMMERCE_TRANSACTION_ID;

/**
 * Activity used to initiate WebView with the SRCi URL. This Activity will handle the callback to
 * the application when SRCi loads {@code callbackUrl}.
 */
public final class WebCheckoutActivity extends AppCompatActivity {
  public static final String CHECKOUT_URL_EXTRA = "CHECKOUT_URL_EXTRA";
  public static final String CALLBACK_SCHEME_EXTRA = "CALLBACK_SCHEME_EXTRA";
  private static final String INTENT_SCHEME = "intent";
  private static final String QUERY_PARAM_TRANSACTION_ID = "transactionId";
  private static final String QUERY_PARAM_STATUS = "status";
  private static final String STATUS_CANCEL = "cancel";
  private static final String STATUS_SUCCESS = "success";
  private static final String TAG = WebCheckoutActivity.class.getSimpleName();

  @SuppressLint("SetJavaScriptEnabled")
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_web_view);

    if (getSupportActionBar() == null) {
      setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
    }

    configureActionBar();

    String url = getIntent().getStringExtra(CHECKOUT_URL_EXTRA);
    Log.d(TAG, "URL to load: " + url);

    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
    final WebView srciWebView = findViewById(R.id.webview);
    srciWebView.getSettings().setJavaScriptEnabled(true);
    srciWebView.getSettings().setDomStorageEnabled(true);
    srciWebView.getSettings().setSupportMultipleWindows(true);
    srciWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);

    //This webViewClient will override an intent loading action to startActivity
    srciWebView.setWebViewClient(
        new WebViewClient() {
          @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return WebCheckoutActivity.this.shouldOverrideUrlLoading(url);
          }

          @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP) @Override
          public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return shouldOverrideUrlLoading(view, request.getUrl().toString());
          }
        });

    srciWebView.setWebChromeClient(
        new WebChromeClient() {
          @SuppressLint("SetJavaScriptEnabled")
          @Override public boolean onCreateWindow(WebView view, boolean isDialog,
              boolean isUserGesture, Message resultMsg) {
            WebView.HitTestResult result = view.getHitTestResult();

            if (result.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
              //If the user has selected an anchor link, open in browser
              Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(result.getExtra()));

              startActivity(browserIntent);

              return false;
            }

            WebView dcfWebView = new WebView(WebCheckoutActivity.this);
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
            dcfWebView.getSettings().setJavaScriptEnabled(true);
            dcfWebView.getSettings().setSupportZoom(true);
            dcfWebView.getSettings().setBuiltInZoomControls(true);
            dcfWebView.getSettings().setSupportMultipleWindows(true);

            view.addView(dcfWebView);

            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(dcfWebView);

            resultMsg.sendToTarget();

            dcfWebView.setWebViewClient(new WebViewClient() {
              @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return WebCheckoutActivity.this.shouldOverrideUrlLoading(url);
              }

              @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP) @Override
              public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return shouldOverrideUrlLoading(view, request.getUrl().toString());
              }
            });

            return true;
          }

          @Override public void onCloseWindow(WebView window) {
            //Need to determine the behavior of SRCi and how we should handle closing windows
          }
        });

    srciWebView.loadUrl(url);
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      setResult(Activity.RESULT_CANCELED);
      finish();

      return true;
    } else {

      return super.onOptionsItemSelected(item);
    }
  }

  @Override protected void onStop() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      CookieManager.getInstance().flush();
    } else {
      CookieSyncManager.getInstance().sync();
    }
    super.onStop();
  }

  private boolean shouldOverrideUrlLoading(String url) {
    URI uri = URI.create(url);
    String urlScheme = uri.getScheme();
    String callbackScheme = getIntent().getStringExtra(CALLBACK_SCHEME_EXTRA);
    //Determine if this hit test is coming from an anchor tag or not. If it is, then it's a link and
    //should be opened in either the browser app, or see if we can suppress the popup and set location
    //Test differences between using URI.create(baseUrl) and Uri.parse(baseUrl)
    //Differences in getting scheme, queryParams, etc.

    //1. Scheme's that match the Merchant's custom defined scheme take precedence
    if (callbackScheme.equals(urlScheme)) {
      Uri uri2 = Uri.parse(urlScheme);
      String status = uri2.getQueryParameter(QUERY_PARAM_STATUS);
      String transactionId = uri2.getQueryParameter(QUERY_PARAM_TRANSACTION_ID);

      if (STATUS_CANCEL.equals(status)) {
        setResult(Activity.RESULT_CANCELED);
      } else if (STATUS_SUCCESS.equals(status)) {
        Intent resultIntent = new Intent().putExtra(COMMERCE_TRANSACTION_ID, transactionId);
        setResult(Activity.RESULT_OK, resultIntent);
      }

      finish();

      return true;
    } else if (urlScheme.equals(INTENT_SCHEME)) {
      //2. Otherwise, intent schemes will be launched if they are in this package
      try {
        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
        ComponentName resolved = intent.resolveActivity(getPackageManager());

        if (resolved != null) {
          String activityPackage = resolved.getPackageName();
          String applicationPackage = getApplication().getApplicationInfo().packageName;

          //Only start the activity associated with his intent if it belongs to this application
          if (activityPackage.equals(applicationPackage)) {
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            startActivity(intent);
          } else {
            setResult(Activity.RESULT_CANCELED);
            finish();
          }
        } else {
          setResult(Activity.RESULT_CANCELED);
          finish();
        }

        return true;
      } catch (URISyntaxException e) {
        Log.e(TAG, "Unable to parse Intent URI. Canceling transaction.", e);

        setResult(Activity.RESULT_CANCELED);
        finish();

        return true;
      }
    } else {
      //4. If nothing else, assume this is the DCF baseUrl to be opened in a new window
      return false;
    }
  }

  private void configureActionBar() {
    ActionBar actionBar = getSupportActionBar();

    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setHomeButtonEnabled(true);
    }
  }
}
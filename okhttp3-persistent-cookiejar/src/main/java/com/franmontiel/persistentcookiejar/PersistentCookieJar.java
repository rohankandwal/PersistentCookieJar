/*
 * Copyright (C) 2016 Francisco José Montiel Navarro.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.franmontiel.persistentcookiejar;

import android.text.TextUtils;
import com.franmontiel.persistentcookiejar.cache.CookieCache;
import com.franmontiel.persistentcookiejar.persistence.CookiePersistor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import okhttp3.Cookie;
import okhttp3.HttpUrl;

public class PersistentCookieJar implements ClearableCookieJar {

  private final CookieCache cache;
  private final CookiePersistor persistor;
  private final boolean ignorePersistance;

  public PersistentCookieJar(
    CookieCache cache,
    CookiePersistor persistor,
    boolean ignorePersistance
  ) {
    this.cache = cache;
    this.persistor = persistor;
    this.ignorePersistance = ignorePersistance;

    this.cache.addAll(persistor.loadAll());
  }

  @Override
  synchronized public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
    cache.addAll(cookies);
    persistor.saveAll(filterPersistentCookies(cookies, ignorePersistance));
  }

  private static List<Cookie> filterPersistentCookies(
    List<Cookie> cookies,
    boolean ignorePersistance
  ) {
    List<Cookie> persistentCookies = new ArrayList<>();

    if (ignorePersistance) {
      return cookies;
    }
    for (Cookie cookie : cookies) {
      if (cookie.persistent()) {
        persistentCookies.add(cookie);
      }
    }
    return persistentCookies;
  }

  /**
   * Function to get saved cookie
   *
   * @param url String containing url of the website for which cookie was saved
   * @param cookieName Name of the cookie you want from saved cookies
   * @return Cookie
   */
  public String getCookie(final String url, final String cookieName) {
    if (TextUtils.isEmpty(url) || TextUtils.isEmpty(cookieName)) {
      return null;
    }
    HttpUrl httpUrl = new HttpUrl.Builder().host(url).build();
    final List<Cookie> cookieList = loadForRequest(httpUrl);
    if (cookieList != null && cookieList.size() > 0) {
      for (final Cookie cookieInfo : cookieList) {
        if (cookieInfo.name().equalsIgnoreCase(cookieName)) {
          return cookieInfo.value();
        }
      }
    }
    return null;
  }

  @Override
  synchronized public List<Cookie> loadForRequest(HttpUrl url) {
    List<Cookie> cookiesToRemove = new ArrayList<>();
    List<Cookie> validCookies = new ArrayList<>();

    for (Iterator<Cookie> it = cache.iterator(); it.hasNext(); ) {
      Cookie currentCookie = it.next();

      if (isCookieExpired(currentCookie)) {
        cookiesToRemove.add(currentCookie);
        it.remove();
      } else if (currentCookie.matches(url)) {
        validCookies.add(currentCookie);
      }
    }

    persistor.removeAll(cookiesToRemove);

    return validCookies;
  }

  private static boolean isCookieExpired(Cookie cookie) {
    return cookie.expiresAt() < System.currentTimeMillis();
  }

  @Override
  synchronized public void clearSession() {
    cache.clear();
    cache.addAll(persistor.loadAll());
  }

  @Override
  synchronized public void clear() {
    cache.clear();
    persistor.clear();
  }
}

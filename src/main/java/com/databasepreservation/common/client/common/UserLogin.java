/**
 * The contents of this file are based on those found at https://github.com/keeps/roda
 * and are subject to the license and copyright detailed in https://github.com/keeps/roda
 */
package com.databasepreservation.common.client.common;

import java.util.List;
import java.util.Vector;

import org.fusesource.restygwt.client.MethodCallback;

import com.databasepreservation.common.client.ClientLogger;
import com.databasepreservation.common.client.common.dialogs.Dialogs;
import com.databasepreservation.common.client.models.user.User;
import com.databasepreservation.common.client.services.AuthenticationService;
import com.databasepreservation.common.client.tools.HistoryManager;
import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.safehtml.shared.UriUtils;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;

import config.i18n.client.ClientMessages;

/**
 * @author Bruno Ferreira <bferreira@keep.pt>
 */
public class UserLogin {
  private static final ClientMessages messages = GWT.create(ClientMessages.class);
  private static final ClientLogger logger = new ClientLogger(UserLogin.class.getName());

  private static UserLogin instance = null;

  private final List<LoginStatusListener> listeners;

  /**
   * @return the singleton instance
   */
  public static UserLogin getInstance() {
    if (instance == null) {
      instance = new UserLogin();
    }
    return instance;
  }

  private UserLogin() {
    listeners = new Vector<>();
  }

  private final CachedAsynRequest<User> getUserRequest = new CachedAsynRequest<User>() {
    @Override
    public void getFromServer(MethodCallback<User> callback) {
      AuthenticationService.Util.call(callback).getAuthenticatedUser();
    }
  };

  /**
   * Get current authenticated user. User is cached and only refreshed when login
   * or logout actions are called.
   *
   * @param callback
   *          call back handler that receives error if failed or AuthOfficeUser if
   *          success.
   */
  public void getAuthenticatedUser(final AsyncCallback<User> callback, boolean ensureIsFresh) {
    if (ensureIsFresh) {
      getUserRequest.clearCache();
    }
    getUserRequest.request(callback);
  }

  public void getAuthenticatedUser(final AsyncCallback<User> callback) {
    getAuthenticatedUser(callback, false);
  }

  /**
   * Login into DBVTK
   */
  public void login() {
    String currentPath = Window.Location.getPath();
    String hash = Window.Location.getHash();
    if (hash.length() > 0) {
      hash = hash.substring(1);
      hash = UriUtils.encode(hash);
    }
    String locale = LocaleInfo.getCurrentLocale().getLocaleName();

    String moduleBaseURL = GWT.getModuleBaseURL();
    moduleBaseURL = moduleBaseURL.substring(0, moduleBaseURL.length() - 2).substring(0, moduleBaseURL.indexOf('/'));

    String brandingIfFalse = Window.Location.getHref().contains("branding=false") ? "&branding=false" : "";

    Window.open(
      moduleBaseURL + "login?service=" + currentPath + "&hash=" + hash + "&locale=" + locale + brandingIfFalse, "_self",
      "");
  }

  public void logout() {
    // 2017-06-1 bferreira: this could probably be changed to use getPath() as
    // the service and pass in a separate hash parameter (similar to what login
    // does)
    String currentURL = Window.Location.getHref().replaceAll("#", "%23");
    String locale = LocaleInfo.getCurrentLocale().getLocaleName();
    String moduleBaseURL = GWT.getModuleBaseURL();
    moduleBaseURL = moduleBaseURL.substring(0, moduleBaseURL.length() - 2).substring(0, moduleBaseURL.indexOf('/'));
    Window.open(moduleBaseURL + "logout?service=" + currentURL + "&locale=" + locale, "_self", "");
    getUserRequest.clearCache();
  }

  /**
   * Add a login status listener
   *
   * @param listener
   */
  public void addLoginStatusListener(LoginStatusListener listener) {
    listeners.add(listener);
  }

  /**
   * Remove a login status listener
   *
   * @param listener
   */
  public void removeLoginStatusListener(LoginStatusListener listener) {
    listeners.remove(listener);
  }

  public void onLoginStatusChanged(User newUser) {
    for (LoginStatusListener listener : listeners) {
      listener.onLoginStatusChanged(newUser);
    }
  }

  public void showSuggestLoginDialog() {
    Dialogs.showConfirmDialog(messages.loginDialogTitle(), messages.casForwardWarning(), messages.basicActionCancel(),
      messages.loginDialogLogin(), new AsyncCallback<Boolean>() {

        @Override
        public void onSuccess(Boolean result) {
          if (result) {
            UserLogin.getInstance().login();
          } else {
            HistoryManager.gotoHome();
          }
        }

        @Override
        public void onFailure(Throwable caught) {
          HistoryManager.gotoHome();
        }
      });
  }
}

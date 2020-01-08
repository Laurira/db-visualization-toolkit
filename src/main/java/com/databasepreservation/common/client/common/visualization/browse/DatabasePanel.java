package com.databasepreservation.common.client.common.visualization.browse;

import java.util.HashMap;
import java.util.Map;

import org.roda.core.data.v2.user.User;

import com.databasepreservation.common.client.ViewerConstants;
import com.databasepreservation.common.client.common.DefaultAsyncCallback;
import com.databasepreservation.common.client.common.RightPanel;
import com.databasepreservation.common.client.common.UserLogin;
import com.databasepreservation.common.client.common.breadcrumb.BreadcrumbPanel;
import com.databasepreservation.common.client.common.sidebar.DatabaseSidebar;
import com.databasepreservation.common.client.common.sidebar.Sidebar;
import com.databasepreservation.common.client.common.utils.ApplicationType;
import com.databasepreservation.common.client.common.utils.JavascriptUtils;
import com.databasepreservation.common.client.common.utils.RightPanelLoader;
import com.databasepreservation.common.client.index.IsIndexed;
import com.databasepreservation.common.client.models.status.collection.CollectionStatus;
import com.databasepreservation.common.client.models.structure.ViewerDatabase;
import com.databasepreservation.common.client.models.structure.ViewerDatabaseStatus;
import com.databasepreservation.common.client.services.AuthenticationService;
import com.databasepreservation.common.client.services.ConfigurationService;
import com.databasepreservation.common.client.services.DatabaseService;
import com.databasepreservation.common.client.tools.BreadcrumbManager;
import com.databasepreservation.common.client.tools.FontAwesomeIconManager;
import com.databasepreservation.common.client.tools.HistoryManager;
import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import config.i18n.client.ClientMessages;

/**
 * @author Bruno Ferreira <bferreira@keep.pt>
 */
public class DatabasePanel extends Composite {
  private static final ClientMessages messages = GWT.create(ClientMessages.class);

  private static Map<String, DatabasePanel> instances = new HashMap<>();

  public static DatabasePanel getInstance(String databaseUUID, boolean initMenu) {
    return instances.computeIfAbsent(databaseUUID, k -> new DatabasePanel(databaseUUID, initMenu));
  }

  public static DatabasePanel getInstance(String databaseUUID, String route, boolean initMenu, Sidebar sidebar) {
    String key = databaseUUID + route;
    instances.computeIfAbsent(key, k -> new DatabasePanel(databaseUUID, initMenu, sidebar));
    return instances.get(databaseUUID + route);
  }

  interface DatabasePanelUiBinder extends UiBinder<Widget, DatabasePanel> {
  }

  private static DatabasePanelUiBinder uiBinder = GWT.create(DatabasePanelUiBinder.class);
  private BreadcrumbPanel breadcrumb = null;

  @UiField
  BreadcrumbPanel breadcrumbServer;
  @UiField
  BreadcrumbPanel breadcrumbDesktop;

  @UiField(provided = true)
  Sidebar sidebar;

  @UiField
  SimplePanel rightPanelContainer;

  @UiField
  MenuBar menu;

  @UiField
  FlowPanel toplevel;

  @UiField
  FlowPanel toolbar;

  private String databaseUUID;
  private ViewerDatabase database = null;
  private CollectionStatus collectionStatus = null;
  private String selectedLanguage;

  private DatabasePanel(String databaseUUID, boolean initMenu) {
    this(databaseUUID, initMenu, null);
  }

  private DatabasePanel(String databaseUUID, boolean initMenu, Sidebar sidebar) {
    this.databaseUUID = databaseUUID;

    if (sidebar == null) {
      this.sidebar = DatabaseSidebar.getInstance(databaseUUID);
    } else {
      this.sidebar = sidebar;
    }

    initWidget(uiBinder.createAndBindUi(this));

    if (initMenu) {
      initMenu();
    }

    if (ApplicationType.getType().equals(ViewerConstants.SERVER)) {
      toolbar.getElement().addClassName("filePreviewToolbar");
      breadcrumb = breadcrumbServer;
      breadcrumbDesktop.removeFromParent();
    } else {
      toolbar.removeFromParent();
      breadcrumb = breadcrumbDesktop;
    }
    breadcrumb.setVisible(true);

    if (databaseUUID == null) {
      BreadcrumbManager.updateBreadcrumb(breadcrumb, BreadcrumbManager.forDatabases());
    } else {
      BreadcrumbManager.updateBreadcrumb(breadcrumb, BreadcrumbManager.loadingDatabase(databaseUUID));
    }
  }

  private void initMenu() {
    menu.addStyleName("user-menu");
    UserLogin.getInstance().addLoginStatusListener(this::buildMenuForUser);

    UserLogin.getInstance().getAuthenticatedUser(new DefaultAsyncCallback<User>() {
      @Override
      public void onSuccess(User user) {
        buildMenuForUser(user);
      }
    });
  }

  private void buildMenuForUser(final User user) {
    menu.clearItems();

    final boolean hideMenu = Window.Location.getHref().contains("branding=false");

    AuthenticationService.Util.call((Boolean authenticationIsEnabled) -> {
      if (authenticationIsEnabled) {
        if (user.isGuest()) {
          menu.addItem(FontAwesomeIconManager.loaded(FontAwesomeIconManager.USER, messages.loginLogin()),
            (Command) () -> UserLogin.getInstance().login());
          MenuBar languagesMenu = new MenuBar(true);

          setLanguageMenu(languagesMenu);

          MenuItem languagesMenuItem = new MenuItem(
            FontAwesomeIconManager.loaded(FontAwesomeIconManager.GLOBE, selectedLanguage), languagesMenu);
          languagesMenuItem.addStyleName("menu-item menu-item-label menu-item-language");
          menu.addItem(languagesMenuItem);
        } else {
          if (!hideMenu) {
            MenuBar subMenu = new MenuBar(true);
            subMenu.addItem(messages.loginLogout(), (Command) () -> UserLogin.getInstance().logout());
            menu.addItem(FontAwesomeIconManager.loaded(FontAwesomeIconManager.USER, user.getFullName()), subMenu);
            AuthenticationService.Util.call((Boolean userIsAdmin) -> {
              if (userIsAdmin) {
                MenuBar administrationMenu = new MenuBar(true);
                administrationMenu.addItem(
                  FontAwesomeIconManager.loaded(FontAwesomeIconManager.ACTIVITY_LOG, messages.activityLogMenuText()),
                  (Command) HistoryManager::gotoActivityLog);
                administrationMenu.addItem(
                  FontAwesomeIconManager.loaded(FontAwesomeIconManager.NETWORK_WIRED, messages.menuTextForJobs()),
                  (Command) HistoryManager::gotoJobs);
                administrationMenu.addItem(
                  FontAwesomeIconManager.loaded(FontAwesomeIconManager.PREFERENCES, messages.menuTextForPreferences()),
                  (Command) HistoryManager::gotoPreferences);
                menu.addItem(messages.menuTextForAdministration(), administrationMenu);
              }

              MenuBar languagesMenu = new MenuBar(true);

              setLanguageMenu(languagesMenu);

              MenuItem languagesMenuItem = new MenuItem(
                FontAwesomeIconManager.loaded(FontAwesomeIconManager.GLOBE, selectedLanguage), languagesMenu);
              languagesMenuItem.addStyleName("menu-item menu-item-label menu-item-language");
              menu.addItem(languagesMenuItem);
            }).userIsAdmin();
          }
        }
      } else {
        menu.addItem(FontAwesomeIconManager.loaded(FontAwesomeIconManager.NEW_UPLOAD, messages.newUpload()),
          (Command) HistoryManager::gotoNewUpload);
        menu.addItem(
          FontAwesomeIconManager.loaded(FontAwesomeIconManager.DATABASES, messages.menusidebar_manageDatabases()),
          (Command) HistoryManager::gotoDatabaseList);
      }
    }).isAuthenticationEnabled();
  }

  private void setLanguageMenu(MenuBar languagesMenu) {
    String locale = LocaleInfo.getCurrentLocale().getLocaleName();

    // Getting supported languages and their display name
    Map<String, String> supportedLanguages = new HashMap<>();

    for (String localeName : LocaleInfo.getAvailableLocaleNames()) {
      if (!"default".equals(localeName)) {
        supportedLanguages.put(localeName, LocaleInfo.getLocaleNativeDisplayName(localeName));
      }
    }

    languagesMenu.clearItems();

    supportedLanguages.keySet().forEach(key -> {
      if (key.equals(locale)) {
        SafeHtmlBuilder b = new SafeHtmlBuilder();
        String iconHTML = "<i class='fa fa-check'></i>";

        b.append(SafeHtmlUtils.fromSafeConstant(supportedLanguages.get(key)));
        b.append(SafeHtmlUtils.fromSafeConstant(iconHTML));

        MenuItem languageMenuItem = new MenuItem(b.toSafeHtml());
        languageMenuItem.addStyleName("menu-item-language-selected");
        languageMenuItem.addStyleName("menu-item-language");
        languagesMenu.addItem(languageMenuItem);
        selectedLanguage = supportedLanguages.get(key);
      } else {
        MenuItem languageMenuItem = new MenuItem(SafeHtmlUtils.fromSafeConstant(supportedLanguages.get(key)),
          () -> JavascriptUtils.changeLocale(key));
        languagesMenu.addItem(languageMenuItem);
        languageMenuItem.addStyleName("menu-item-language");
      }
    });
  }

  public void load(RightPanelLoader rightPanelLoader, String toSelect) {
    GWT.log("load. uuid: " + databaseUUID + ", database: " + database);
    if (databaseUUID != null && (database == null || !ViewerDatabaseStatus.AVAILABLE.equals(database.getStatus()))) {
      // need to load database (not present or not available), go get it
      GWT.log("getting db");
      loadPanelWithDatabase(rightPanelLoader, toSelect);
    } else {
      loadPanel(rightPanelLoader, toSelect);
    }
  }

  private void loadPanelWithDatabase(final RightPanelLoader rightPanelLoader, String toSelect) {
    DatabaseService.Util.call((IsIndexed result) -> {
      database = (ViewerDatabase) result;
      loadPanel(rightPanelLoader, toSelect);
    }).retrieve(databaseUUID, databaseUUID);
  }

  private void loadPanel(RightPanelLoader rightPanelLoader, String toSelect) {
    GWT.log("have db: " + database + " sb.init: " + sidebar.isInitialized());
    ConfigurationService.Util.call((CollectionStatus status) -> {
      collectionStatus = status;

      RightPanel rightPanel = rightPanelLoader.load(database, collectionStatus);

      if (database != null && !sidebar.isInitialized()) {
        sidebar.init(database);
        sidebar.select(toSelect);
      }

      if (rightPanel != null) {
        rightPanel.handleBreadcrumb(breadcrumb);
        rightPanelContainer.setWidget(rightPanel);
        rightPanel.setVisible(true);
        sidebar.select(toSelect);
      }
    }).getCollectionStatus(database.getUuid(), database.getUuid());
  }

  public void setTopLevelPanelCSS(String css) {
    toplevel.addStyleName(css);
  }
}

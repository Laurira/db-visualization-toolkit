package com.databasepreservation.common.server;

import static com.databasepreservation.common.shared.ViewerConstants.SOLR_INDEX_ROW_COLLECTION_NAME_PREFIX;
import static com.databasepreservation.common.shared.ViewerConstants.SOLR_SEARCHES_DATABASE_UUID;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import com.databasepreservation.common.server.index.DatabaseRowsSolrManager;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.roda.core.data.exceptions.AuthenticationDeniedException;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RODAException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.utils.JsonUtils;
import org.roda.core.data.v2.index.IndexResult;
import org.roda.core.data.v2.index.facet.Facets;
import org.roda.core.data.v2.index.filter.Filter;
import org.roda.core.data.v2.index.filter.SimpleFilterParameter;
import org.roda.core.data.v2.index.sort.Sorter;
import org.roda.core.data.v2.index.sublist.Sublist;
import org.roda.core.data.v2.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.databasepreservation.common.client.BrowserService;
import com.databasepreservation.common.server.controller.SIARDController;
import com.databasepreservation.common.server.controller.UserLoginController;
import com.databasepreservation.common.server.index.factory.SolrClientFactory;
import com.databasepreservation.common.server.index.schema.SolrDefaultCollectionRegistry;
import com.databasepreservation.common.server.index.utils.SolrUtils;
import com.databasepreservation.common.shared.ProgressData;
import com.databasepreservation.common.shared.ValidationProgressData;
import com.databasepreservation.common.shared.ViewerStructure.IsIndexed;
import com.databasepreservation.common.shared.ViewerStructure.ViewerDatabase;
import com.databasepreservation.common.shared.ViewerStructure.ViewerMetadata;
import com.databasepreservation.common.shared.ViewerStructure.ViewerRow;
import com.databasepreservation.common.shared.ViewerStructure.ViewerSIARDBundle;
import com.databasepreservation.common.shared.ViewerStructure.ViewerTable;
import com.databasepreservation.common.shared.client.common.search.SavedSearch;
import com.databasepreservation.common.shared.client.common.search.SearchField;
import com.databasepreservation.common.shared.client.common.search.SearchInfo;
import com.databasepreservation.common.shared.client.common.utils.BrowserServiceUtils;
import com.databasepreservation.common.shared.models.wizardParameters.ConnectionParameters;
import com.databasepreservation.common.shared.models.wizardParameters.CustomViewsParameters;
import com.databasepreservation.common.shared.models.wizardParameters.ExportOptionsParameters;
import com.databasepreservation.common.shared.models.wizardParameters.MetadataExportOptionsParameters;
import com.databasepreservation.common.shared.models.wizardParameters.TableAndColumnsParameters;
import com.databasepreservation.common.utils.UserUtility;
import com.databasepreservation.common.utils.UserUtility.Authorization;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;

/**
 * The server side implementation of the RPC service.
 */
@SuppressWarnings("serial")
public class BrowserServiceImpl extends RemoteServiceServlet implements BrowserService {
  private static final Logger LOGGER = LoggerFactory.getLogger(BrowserServiceImpl.class);

  /**
   * Overridden to load the gwt.codeserver.port system property.
   *
   * @param config
   */
  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    SolrClientFactory.get();
  }

  /**
   * Called by the servlet container to indicate to a servlet that the servlet is
   * being taken out of service.
   */
  @Override
  public void destroy() {
    super.destroy();

    try {
      SolrClientFactory.get().getSolrClient().close();
    } catch (IOException e) {
      LOGGER.error("Stopping SolrClient", e);
    }
  }

  /**
   * Escape an html string. Escaping data received from the client helps to
   * prevent cross-site script vulnerabilities.
   *
   * @param html
   *          the html string to escape
   * @return the escaped string
   */
  private String escapeHtml(String html) {
    if (html == null) {
      return null;
    }
    return html.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
  }

  public IndexResult<ViewerDatabase> findDatabases(Filter filter, Sorter sorter, Sublist sublist, Facets facets,
    String localeString) throws GenericException, AuthorizationDeniedException, RequestNotValidException {
    Authorization.allowIfAdmin(getThreadLocalRequest());
    return ViewerFactory.getSolrManager().find(ViewerDatabase.class, filter, sorter, sublist, facets);
  }

  public IndexResult<SavedSearch> findSavedSearches(String databaseUUID, Filter filter, Sorter sorter, Sublist sublist,
    Facets facets, String localeString)
    throws GenericException, AuthorizationDeniedException, RequestNotValidException, NotFoundException {
    Authorization.checkFilteringPermission(getThreadLocalRequest(), databaseUUID, filter, SavedSearch.class);
    return ViewerFactory.getSolrManager().find(SavedSearch.class, filter, sorter, sublist, facets);
  }

  @Override
  public <T extends IsIndexed> T retrieve(String databaseUUID, String classNameToReturn, String id)
    throws AuthorizationDeniedException, GenericException, NotFoundException {
    Class<T> classToReturn = parseClass(classNameToReturn);
    T result = ViewerFactory.getSolrManager().retrieve(classToReturn, id);
    Authorization.checkRetrievalPermission(getThreadLocalRequest(), databaseUUID, classToReturn, result);
    return result;
  }

  @Override
  public IndexResult<ViewerRow> findRows(String databaseUUID, Filter filter, Sorter sorter, Sublist sublist,
    Facets facets, String localeString)
    throws GenericException, AuthorizationDeniedException, RequestNotValidException {
    try {
      Authorization.checkDatabaseAccessPermission(getThreadLocalRequest(), databaseUUID);
    } catch (NotFoundException e) {
      throw new RequestNotValidException("Invalid database UUID: " + databaseUUID, e);
    }

    return ViewerFactory.getSolrManager().findRows(databaseUUID, filter, sorter, sublist, facets);
  }

  @Override
  public Long countRows(String databaseUUID, Filter filter)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException {
    try {
      Authorization.checkDatabaseAccessPermission(getThreadLocalRequest(), databaseUUID);
    } catch (NotFoundException e) {
      throw new RequestNotValidException("Invalid database UUID: " + databaseUUID, e);
    }

    return ViewerFactory.getSolrManager().countRows(databaseUUID, filter);
  }

  @Override
  public ViewerRow retrieveRows(String databaseUUID, String rowUUID)
    throws AuthorizationDeniedException, GenericException, NotFoundException {
    Authorization.checkDatabaseAccessPermission(getThreadLocalRequest(), databaseUUID);
    return ViewerFactory.getSolrManager().retrieveRows(databaseUUID, rowUUID);
  }

  @Override
  public String getSolrQueryString(Filter filter, Sorter sorter, Sublist sublist, Facets facets)
    throws GenericException, RequestNotValidException {
    // does not retrieve data from index => safe to ignore authorization
    return SolrUtils.getSolrQuery(filter, sorter, sublist, facets);
  }

  @Override
  public String saveSearch(String name, String description, String tableUUID, String tableName, String databaseUUID,
    SearchInfo searchInfo)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException {
    Authorization.checkDatabaseAccessPermission(getThreadLocalRequest(), databaseUUID);

    String searchInfoJson = JsonUtils.getJsonFromObject(searchInfo);

    SavedSearch savedSearch = new SavedSearch();
    savedSearch.setUUID(SolrUtils.randomUUID());
    savedSearch.setName(name);
    savedSearch.setDescription(description);
    savedSearch.setDatabaseUUID(databaseUUID);
    savedSearch.setTableUUID(tableUUID);
    savedSearch.setTableName(tableName);
    savedSearch.setSearchInfoJson(searchInfoJson);

    ViewerFactory.getSolrManager().addSavedSearch(savedSearch);

    return savedSearch.getUUID();
  }

  @Override
  public void editSearch(String databaseUUID, String savedSearchUUID, String name, String description)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException {
    // get the saved search
    SavedSearch savedSearch = ViewerFactory.getSolrManager().retrieve(SavedSearch.class, savedSearchUUID);
    // authorise viewing the saved search
    Authorization.checkSavedSearchPermission(getThreadLocalRequest(), databaseUUID, savedSearch);
    // authorise editing the saved search
    Authorization.allowIfAdminOrManager(getThreadLocalRequest());

    ViewerFactory.getSolrManager().editSavedSearch(savedSearchUUID, name, description);
  }

  @Override
  public void deleteSearch(String databaseUUID, String savedSearchUUID)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException, NotFoundException {
    // get the saved search
    SavedSearch savedSearch = ViewerFactory.getSolrManager().retrieve(SavedSearch.class, savedSearchUUID);
    // authorise viewing the saved search
    Authorization.checkSavedSearchPermission(getThreadLocalRequest(), databaseUUID, savedSearch);
    // authorise editing the saved search
    Authorization.allowIfAdminOrManager(getThreadLocalRequest());

    ViewerFactory.getSolrManager().deleteSavedSearch(savedSearchUUID);
  }

  @Override
  public Boolean deleteDatabaseCollection(String databaseUUID) {
    ViewerFactory.getSolrManager().deleteDatabasesCollection(databaseUUID);

    return true;
  }

  @Override
  public Boolean deleteRowsCollection(String databaseUUID) {
    try {
      final String collectionName = SOLR_INDEX_ROW_COLLECTION_NAME_PREFIX + databaseUUID;
      if (SolrClientFactory.get().deleteCollection(collectionName)) {
        Filter savedSearchFilter = new Filter(new SimpleFilterParameter(SOLR_SEARCHES_DATABASE_UUID, databaseUUID));
        SolrUtils.delete(ViewerFactory.getSolrClient(), SolrDefaultCollectionRegistry.get(SavedSearch.class),
            savedSearchFilter);

        ViewerFactory.getSolrManager().markDatabaseCollection(databaseUUID, ViewerDatabase.Status.METADATA_ONLY);
        return true;
      }
    } catch (GenericException | RequestNotValidException e) {
      LOGGER.error("Error trying to remove the collection from Solr", e);
      return false;
    }
    return false;
  }

  @Override
  public Boolean deleteAllCollections(String databaseUUID) {
    if (deleteRowsCollection(databaseUUID)) {
      return deleteDatabaseCollection(databaseUUID);
    }
    return false;
  }

  @Override
  public Boolean isAuthenticationEnabled() throws RODAException {
    return ViewerConfiguration.getInstance().getIsAuthenticationEnabled();
  }

  @Override
  public List<SearchField> getSearchFields(ViewerTable viewerTable) throws GenericException {
    // does not retrieve data from index => safe to ignore authorization
    return BrowserServiceUtils.getSearchFieldsFromTable(viewerTable);
  }

  @SuppressWarnings("unchecked")
  private <T extends IsIndexed> Class<T> parseClass(String classNameToReturn) throws GenericException {
    Class<T> classToReturn;
    try {
      classToReturn = (Class<T>) Class.forName(classNameToReturn);
    } catch (ClassNotFoundException e) {
      throw new GenericException("Could not find class " + classNameToReturn);
    }
    return classToReturn;
  }

  public User getAuthenticatedUser() throws RODAException {
    User user = UserUtility.getUser(this.getThreadLocalRequest());
    LOGGER.debug("Serving user {}", user);
    return user;
  }

  public User login(String username, String password) throws AuthenticationDeniedException, GenericException {
    User user = UserLoginController.login(username, password, this.getThreadLocalRequest());
    LOGGER.debug("Logged user {}", user);
    return user;
  }

  @Override
  public String uploadSIARD(String path) throws GenericException {
    return SIARDController.loadFromLocal(path);
  }

  @Override
  public String uploadSIARD(String path, String databaseUUID) throws GenericException {
    return SIARDController.loadFromLocal(path, databaseUUID);
  }

  @Override
  public ViewerDatabase uploadSIARDStatus(String databaseUUID)
    throws AuthorizationDeniedException, NotFoundException, GenericException {
    return retrieve(databaseUUID, ViewerDatabase.class.getName(), databaseUUID);
  }

  @Override
  public String getReport(String databaseUUID)
    throws GenericException, AuthorizationDeniedException, NotFoundException {
    ViewerDatabase dummy = new ViewerDatabase();
    dummy.setUUID(databaseUUID);
    Authorization.checkRetrievalPermission(getThreadLocalRequest(), databaseUUID, ViewerDatabase.class, dummy);
    return SIARDController.getReportFileContents(databaseUUID);
  }

  @Override
  public String getApplicationType() {
    return System.getProperty("env", "server");
  }

  public String getClientMachine() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      LOGGER.debug("UnkownHostException");
    }
    return "";
  }

  @Override
  public String uploadMetadataSIARD(String databaseUUID, String path) throws GenericException {
    return SIARDController.loadMetadataFromLocal(databaseUUID, path);
  }

  @Override
  public String findSIARDFile(String path) throws GenericException, RequestNotValidException {
    return ViewerFactory.getSolrManager().findSIARDFile(path);
  }

  @Override
  public String getDatabaseImportModules() throws GenericException {
    return SIARDController.getDatabaseImportModules();
  }

  @Override
  public String getDatabaseExportModules() throws GenericException {
    return SIARDController.getDatabaseExportModules();
  }

  @Override
  public String getSIARDExportModule(String moduleName) throws GenericException {
    return SIARDController.getSIARDExportModule(moduleName);
  }

  @Override
  public String getSIARDExportModules() throws GenericException {
    return SIARDController.getSIARDExportModules();
  }

  @Override
  public String getSchemaInformation(String databaseUUID, ConnectionParameters values) throws GenericException {
    return SIARDController.getDatabaseMetadata(databaseUUID, values);
  }

  @Override
  public boolean testConnection(String databaseUUID, String parametersJson) throws GenericException {
    return SIARDController.testConnection(databaseUUID, parametersJson);
  }

  @Override
  public List<List<String>> validateCustomViewQuery(String databaseUUID, ConnectionParameters parameters, String query)
    throws GenericException {
    return SIARDController.validateCustomViewQuery(databaseUUID, parameters, query);
  }

  @Override
  public boolean createSIARD(String UUID, ConnectionParameters connectionParameters,
    TableAndColumnsParameters tableAndColumnsParameters, CustomViewsParameters customViewsParameters,
    ExportOptionsParameters exportOptionsParameters, MetadataExportOptionsParameters metadataExportOptionsParameters)
    throws GenericException {
    return SIARDController.createSIARD(UUID, connectionParameters, tableAndColumnsParameters, customViewsParameters,
      exportOptionsParameters, metadataExportOptionsParameters);
  }

  @Override
  public boolean migrateToDBMS(String databaseUUID, String siardVersion, String siardPath,
    ConnectionParameters connectionParameters)
    throws GenericException {
    return SIARDController.migrateToDBMS(databaseUUID, siardVersion, siardPath, connectionParameters);
  }

  @Override
  public boolean migrateToSIARD(String databaseUUID, String siardVersion, String siardPath,
    TableAndColumnsParameters tableAndColumnsParameters, ExportOptionsParameters exportOptionsParameters,
    MetadataExportOptionsParameters metadataExportOptions) throws GenericException {
    return SIARDController.migrateToSIARD(databaseUUID, siardVersion, siardPath, tableAndColumnsParameters,
      exportOptionsParameters,
      metadataExportOptions);
  }

  @Override
  public String generateUUID() {
    return SolrUtils.randomUUID();
  }

  @Override
  public ViewerMetadata updateMetadataInformation(ViewerMetadata metadata, ViewerSIARDBundle bundleSiard,
    String databaseUUID, String path) throws GenericException {
    return SIARDController.updateMetadataInformation(metadata, bundleSiard, databaseUUID, path);
  }

  @Override
  public boolean validateSIARD(String databaseUUID, String SIARDPath, String validationReportPath,
    String allowedTypePath) throws GenericException {
    return SIARDController.validateSIARD(databaseUUID, SIARDPath, validationReportPath, allowedTypePath);
  }

  @Override
  public ProgressData getProgressData(String uuid) {
    return ProgressData.getInstance(uuid);
  }

  @Override
  public ValidationProgressData getValidationProgressData(String uuid) {
    return ValidationProgressData.getInstance(uuid);
  }

  @Override
  public void clearValidationProgressData(String uuid) {
    ValidationProgressData.clear(uuid);
  }

  @Override
  public void updateStatusValidate(String uuid, ViewerDatabase.ValidationStatus status){
    SIARDController.updateStatusValidate(uuid, status);
  }

  @Override
  public String getDateTimeHumanized(String dateTimeString) {
    if (StringUtils.isBlank(dateTimeString))
      return dateTimeString;
    DateTime dateTime = DateTime.parse(dateTimeString);

    DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm (z)").withZone(DateTimeZone.UTC);
    return dateTimeFormatter.print(dateTime);
  }
}
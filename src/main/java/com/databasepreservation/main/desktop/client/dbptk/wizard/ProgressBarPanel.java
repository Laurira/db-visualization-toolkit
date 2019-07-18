package com.databasepreservation.main.desktop.client.dbptk.wizard;

import com.databasepreservation.main.common.client.BrowserService;
import com.databasepreservation.main.common.shared.SIARDProgressData;
import com.databasepreservation.main.common.shared.client.common.DefaultAsyncCallback;
import com.databasepreservation.main.common.shared.client.common.Progressbar;
import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import config.i18n.client.ClientMessages;

/**
 * @author Miguel Guimarães <mguimaraes@keep.pt>
 */
public class ProgressBarPanel extends Composite {
  private static final ClientMessages messages = GWT.create(ClientMessages.class);

  interface ProgressBarPanelUiBinder extends UiBinder<Widget, ProgressBarPanel> {
  }

  public static ProgressBarPanel createInstance(String uuid) {
    return new ProgressBarPanel(uuid);
  }

  private static ProgressBarPanelUiBinder uiBinder = GWT.create(ProgressBarPanelUiBinder.class);

  private Timer autoUpdateTimer = new Timer() {
    @Override
    public void run() {
      ProgressBarPanel.this.update();
    }
  };

  @UiField
  FlowPanel content;

  @UiField
  Progressbar progressBar;

  private final String uuid;

  private ProgressBarPanel(String uuid) {
    initWidget(uiBinder.createAndBindUi(this));
    this.uuid = uuid;
    update();
  }

  private void init() {
    stopUpdating();
    autoUpdateTimer.scheduleRepeating(1000);
  }

  @Override
  protected void onAttach() {
    init();
    super.onAttach();
  }

  private void update() {
    BrowserService.Util.getInstance().getProgressData(uuid, new DefaultAsyncCallback<SIARDProgressData>() {
      @Override
      public void onSuccess(SIARDProgressData result) {
        update(result);
      }
    });
  }

  private void update(SIARDProgressData progressData) {

    GWT.log("rows:" + progressData.getProcessedRows());
    GWT.log("Total Rows: " + progressData.getTotalRows());

    int currentGlobalPercent = new Double(
      ((progressData.getProcessedRows() * 1.0D) / progressData.getTotalRows()) * 100).intValue();
    progressBar.setCurrent(currentGlobalPercent);

    if (progressData.isDatabaseStructureRetrieved()) {

      final String totalTablesPercentage = buildPercentageMessage(messages.progressBarPanelTables(),
        progressData.getProcessedTables(), progressData.getTotalTables());
      final String totalRowsPercentage = buildPercentageMessage(messages.progressBarPanelRows(),
        progressData.getProcessedRows(), progressData.getTotalRows());
      final String currentTable = buildSimpleMessage(messages.progressBarPanelCurrentTables(),
        progressData.getCurrentTableName());
      final String currentTableRowsPercentage = buildPercentageMessage(messages.progressBarPanelCurrentRows(),
        progressData.getCurrentProcessedTableRows(), progressData.getCurrentTableTotalRows());

      addMessageToContent(totalTablesPercentage);
      addMessageToContent(totalRowsPercentage);
      addMessageToContent(currentTable);
      addMessageToContent(currentTableRowsPercentage);
    } else {
      Label newMessage = new Label(messages.retrievingTableStructure());
      content.add(newMessage);
    }

    if (progressData.isFinished()) {
      stopUpdating();
    }
    /*
     * 
     * 
     * // create textual log
     * 
     * // extra messages to smoothen the log if (database.getCurrentSchemaName() !=
     * null &&
     * !database.getCurrentSchemaName().equals(result.getCurrentSchemaName())) { //
     * last message about a completed schema, schemas and rows at 100%
     * addMessageToContent(buildMessage(database.getCurrentSchemaName(),
     * database.getIngestedSchemas(), database.getTotalSchemas(),
     * database.getCurrentTableName(), database.getTotalTables(),
     * database.getTotalTables(), database.getTotalRows(),
     * database.getTotalRows())); } else { if (database.getCurrentTableName() !=
     * null && !database.getCurrentTableName().equals(result.getCurrentTableName()))
     * { // last message about a completed schema, rows at 100%
     * addMessageToContent(buildMessage(database.getCurrentSchemaName(),
     * database.getIngestedSchemas(), database.getTotalSchemas(),
     * database.getCurrentTableName(), database.getIngestedTables(),
     * database.getTotalTables(), database.getTotalRows(),
     * database.getTotalRows())); } }
     * 
     * // current message
     * addMessageToContent(buildMessage(result.getCurrentSchemaName(),
     * result.getIngestedSchemas(), result.getTotalSchemas(),
     * result.getCurrentTableName(), result.getIngestedTables(),
     * result.getTotalTables(), result.getIngestedRows(), result.getTotalRows()));
     * 
     * database = result; } else if
     * (result.getStatus().equals(ViewerDatabase.Status.AVAILABLE)) {
     * Toast.showInfo("Success", "Database \"" + database.getMetadata().getName() +
     * "\" has been loaded."); HistoryManager.gotoDatabase(result.getUUID()); } else
     * if (result.getStatus().equals(ViewerDatabase.Status.ERROR)) {
     * addMessageToContent("The database could not be converted due to an error. Check the logs for details."
     * ); stopUpdating(); } else if
     * (result.getStatus().equals(ViewerDatabase.Status.REMOVING)) {
     * addMessageToContent("The database is being removed and can not be displayed."
     * ); stopUpdating(); } else {
     * addMessageToContent("The database is in an unhandled status.");
     * stopUpdating(); }
     */
  }

  @Override
  protected void onDetach() {
    stopUpdating();
    super.onDetach();
  }

  private void stopUpdating() {
    if (autoUpdateTimer != null) {
      autoUpdateTimer.cancel();
    }
  }

  private void addMessageToContent(String message) {
    Label newMessage = new Label(message);
    if (content.getWidgetCount() > 0) {
      Label lastMessage = (Label) content.getWidget(content.getWidgetCount() - 1);
      if (!newMessage.getText().equals(lastMessage.getText())) {
        content.add(newMessage);
        content.getElement().setScrollTop(content.getElement().getScrollHeight());
      }
    } else {
      content.add(newMessage);
      content.getElement().setScrollTop(content.getElement().getScrollHeight());
    }
  }

  private String buildPercentageMessage(final String type, final long processed, final long total) {
    // Examples Table: X of Y (Z%), Rows: X of Y (Z%), Rows on current table: X of Y
    // (Z%)
    StringBuilder sb = new StringBuilder();

    sb.append(type).append(" ").append(processed).append(" ").append(messages.of()).append(" ").append(total);

    float percent = 0;
    if (total > 0) {
      percent = (processed * 1.0F) / total;
    }

    sb.append(" (").append(NumberFormat.getPercentFormat().format(percent)).append(")");
    sb.append("\n\n");

    return sb.toString();
  }

  private String buildSimpleMessage(final String type, final String message) {
    // Example: Current table: <name>
    return type + " " + message + "\n\n";
  }
}
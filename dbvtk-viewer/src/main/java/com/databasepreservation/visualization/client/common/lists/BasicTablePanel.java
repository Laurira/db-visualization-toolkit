package com.databasepreservation.visualization.client.common.lists;

import java.util.Iterator;
import java.util.List;

import com.databasepreservation.visualization.shared.client.widgets.MyCellTableResources;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.event.dom.client.ScrollHandler;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;

/**
 * Widget with a title widget, a description or information widget and a
 * synchronous table (a CellTable using a ListDataProvider).
 * 
 * @author Bruno Ferreira <bferreira@keep.pt>
 * @param <C>
 *          Type for the column, used by inner class ColumnInfo as Column<C,
 *          SafeHtml>
 */
public class BasicTablePanel<C> extends Composite {
  interface BasicTablePanelUiBinder extends UiBinder<Widget, BasicTablePanel> {
  }

  private static BasicTablePanelUiBinder uiBinder = GWT.create(BasicTablePanelUiBinder.class);

  private ScrollPanel displayScroll;
  private SimplePanel displayScrollWrapper;

  public static class ColumnInfo<C> {
    private Column<C, ?> column;
    private double widthEM;
    private SafeHtml header;

    public ColumnInfo(SafeHtml header, double widthEM, Column<C, ?> column, String... addCellStyleNames) {
      this.header = header;
      this.widthEM = widthEM;
      this.column = column;
      for (String addCellStyleName : addCellStyleNames) {
        this.column.setCellStyleNames(addCellStyleName);
      }
    }

    public ColumnInfo(String header, double widthEM, Column<C, ?> column, String... addCellStyleNames) {
      this(SafeHtmlUtils.fromString(header), widthEM, column, addCellStyleNames);
    }
  }

  @UiField
  SimplePanel header;
  @UiField
  SimplePanel info;
  @UiField
  SimplePanel table;

  @SafeVarargs
  public BasicTablePanel(Widget headerContent, SafeHtml infoContent, Iterator<C> rowItems, ColumnInfo<C>... columns) {
    this(headerContent, new HTMLPanel(infoContent), rowItems, columns);
  }

  @SafeVarargs
  public BasicTablePanel(Widget headerContent, Widget infoContent, Iterator<C> rowItems, ColumnInfo<C>... columns) {
    initWidget(uiBinder.createAndBindUi(this));

    // set widgets
    header.setWidget(headerContent);
    info.setWidget(infoContent);

    displayScroll = new ScrollPanel(createTable(rowItems, columns));
    displayScrollWrapper = new SimplePanel(displayScroll);
    displayScrollWrapper.addStyleName("my-asyncdatagrid-display-scroll-wrapper");
    table.setWidget(displayScrollWrapper);

    displayScroll.addScrollHandler(new ScrollHandler() {
      @Override
      public void onScroll(ScrollEvent event) {
        handleScrollChanges();
      }
    });
    handleScrollChanges();
  }

  @Override
  public void setVisible(boolean visible) {
    super.setVisible(visible);
    handleScrollChanges();
  }

  public void handleScrollChanges() {
    GWT.log("maximum: " + displayScroll.getMaximumHorizontalScrollPosition());
    if (displayScroll.getMaximumHorizontalScrollPosition() > 0) {
      double percent = displayScroll.getHorizontalScrollPosition() * 100F
        / displayScroll.getMaximumHorizontalScrollPosition();

      com.google.gwt.core.shared.GWT.log(String.valueOf(percent));

      if (percent > 0) {
        // show left shadow
        displayScrollWrapper.addStyleName("my-asyncdatagrid-display-scroll-wrapper-left");
      } else {
        // hide left shadow
        displayScrollWrapper.removeStyleName("my-asyncdatagrid-display-scroll-wrapper-left");
      }

      if (percent < 100) {
        // show right shadow
        displayScrollWrapper.addStyleName("my-asyncdatagrid-display-scroll-wrapper-right");
      } else {
        // hide right shadow
        displayScrollWrapper.removeStyleName("my-asyncdatagrid-display-scroll-wrapper-right");
      }
    } else {
      // hide both shadows
      displayScrollWrapper.removeStyleName("my-asyncdatagrid-display-scroll-wrapper-left");
      displayScrollWrapper.removeStyleName("my-asyncdatagrid-display-scroll-wrapper-right");
    }
  }

  public BasicTablePanel(Widget headerContent, String infoContent) {
    initWidget(uiBinder.createAndBindUi(this));

    // set widgets
    header.setWidget(headerContent);

    SafeHtmlBuilder b = new SafeHtmlBuilder();
    b.append(SafeHtmlUtils.fromSafeConstant("<div class=\"field\">"));
    b.append(SafeHtmlUtils.fromSafeConstant("<div class=\"label\">"));
    b.append(SafeHtmlUtils.fromString(infoContent));
    b.append(SafeHtmlUtils.fromSafeConstant("</div>"));
    b.append(SafeHtmlUtils.fromSafeConstant("</div>"));
    info.setWidget(new HTMLPanel(b.toSafeHtml()));

    table.setVisible(false);
  }

  @SafeVarargs
  private final CellTable<C> createTable(Iterator<C> rowItems, ColumnInfo<C>... columns) {
    // create table
    CellTable<C> cellTable = new CellTable<>(Integer.MAX_VALUE,
      (MyCellTableResources) GWT.create(MyCellTableResources.class));
    cellTable.setKeyboardSelectionPolicy(HasKeyboardSelectionPolicy.KeyboardSelectionPolicy.DISABLED);
    cellTable
      .setLoadingIndicator(new HTML(
        SafeHtmlUtils
          .fromSafeConstant("<div class='spinner'><div class='double-bounce1'></div><div class='double-bounce2'></div></div>")));
    cellTable.addStyleName("table-info my-asyncdatagrid-display");

    // add columns
    for (ColumnInfo<C> column : columns) {
      cellTable.addColumn(column.column, column.header);
      cellTable.setColumnWidth(column.column, column.widthEM, Style.Unit.EM);
    }

    // fetch rows
    ListDataProvider<C> dataProvider = new ListDataProvider<C>();
    dataProvider.addDataDisplay(cellTable);
    List<C> list = dataProvider.getList();
    while (rowItems.hasNext()) {
      C rowItem = rowItems.next();
      list.add(rowItem);
    }

    return cellTable;
  }
}

package com.databasepreservation.common.server.index.utils;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.databasepreservation.common.client.index.IndexResult;
import com.databasepreservation.common.client.index.sort.Sorter;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.common.params.CursorMarkParams;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.RequestNotValidException;
import com.databasepreservation.common.client.index.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.databasepreservation.common.client.models.structure.ViewerRow;

/**
 * @author Miguel Guimarães <mguimaraes@keep.pt>
 */
public class IndexResultIterator implements Iterator<ViewerRow> {

  private static final Logger LOGGER = LoggerFactory.getLogger(IndexResultIterator.class);

  public static final int DEFAULT_PAGE_SIZE = 1000;
  public static final int DEFAULT_RETRIES = 100;
  public static final int DEFAULT_SLEEP_BETWEEN_RETRIES = 10000;

  private int pageSize = DEFAULT_PAGE_SIZE;
  private int retries = DEFAULT_RETRIES;
  private int sleepBetweenRetries = DEFAULT_SLEEP_BETWEEN_RETRIES;

  private IndexResult<ViewerRow> result = null;
  private int indexInResult = 0;
  private String cursorMark = CursorMarkParams.CURSOR_MARK_START;
  private String nextCursorMark = CursorMarkParams.CURSOR_MARK_START;

  private final SolrClient index;
  private final Filter filter;
  private final Sorter sorter;
  private final String databaseUUID;
  private final List<String> fieldsToReturn;
  private final Map<String, String> extraParameters;

  private ViewerRow next = null;

  public IndexResultIterator(SolrClient index, String databaseUUID, Filter filter, Sorter sorter, List<String> fieldsToReturn, Map<String, String> extraParameters) {
    this.index = index;
    this.filter = filter;
    this.sorter = sorter;
    this.databaseUUID = databaseUUID;
    this.fieldsToReturn = fieldsToReturn;
    this.extraParameters = extraParameters;

    getCurrentAndPrepareNext();
  }

  private ViewerRow getCurrentAndPrepareNext() {
    ViewerRow current = next;

    // ensure index result is renewed
    if (result == null || result.getResults().size() == indexInResult) {
      indexInResult = 0;

      cursorMark = nextCursorMark;
      result = null;
      nextCursorMark = null;
      int availableRetries = retries;

      do {
        try {
          Pair<IndexResult<ViewerRow>, String> page = SolrUtils.findRows(index, databaseUUID, filter, sorter, pageSize,
            cursorMark, fieldsToReturn, extraParameters);
          result = page.getFirst();
          nextCursorMark = page.getSecond();

        } catch (GenericException | RequestNotValidException e) {
          if (availableRetries > 0) {
            availableRetries--;
            LOGGER.warn("Error getting next page from Solr, retrying in {}ms...", sleepBetweenRetries);
            try {
              Thread.sleep(sleepBetweenRetries);
            } catch (InterruptedException e1) {
              // do nothing
            }
          } else {
            LOGGER.error("Error getting next page from Solr, no more retries.", e);
            throw new NoSuchElementException("Error getting next item in list: " + e.getMessage());
          }
        }
      } while (result == null);
    }

    if (indexInResult < result.getResults().size()) {
      this.next = result.getResults().get(indexInResult++);
    } else {
      this.next = null;
    }

    return current;
  }

  @Override
  public boolean hasNext() {
    return next != null;
  }

  @Override
  public ViewerRow next() {
    return getCurrentAndPrepareNext();
  }

  /**
   * @return the pageSize
   */
  public int getPageSize() {
    return pageSize;
  }

  /**
   * @param pageSize
   *          the pageSize to set
   */
  public void setPageSize(int pageSize) {
    this.pageSize = pageSize;
  }

  /**
   * @return the retries
   */
  public int getRetries() {
    return retries;
  }

  /**
   * @param retries
   *          the retries to set
   */
  public void setRetries(int retries) {
    this.retries = retries;
  }

  /**
   * @return the sleepBetweenRetries
   */
  public int getSleepBetweenRetries() {
    return sleepBetweenRetries;
  }

  /**
   * @param sleepBetweenRetries
   *          the sleepBetweenRetries to set
   */
  public void setSleepBetweenRetries(int sleepBetweenRetries) {
    this.sleepBetweenRetries = sleepBetweenRetries;
  }

  /**
   * Gets the total count of objects as reported by underlying Solr requests.
   *
   * @return
   */
  public long getTotalCount() {
    return result != null ? result.getTotalCount() : -1;
  }
}

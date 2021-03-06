package com.databasepreservation.common.client.tools;

import java.util.HashSet;
import java.util.Set;

import com.databasepreservation.common.client.index.filter.Filter;
import com.databasepreservation.common.client.index.filter.FilterParameter;
import com.databasepreservation.common.client.index.filter.SimpleFilterParameter;

import com.databasepreservation.common.client.ViewerConstants;

/**
 * @author Bruno Ferreira <bferreira@keep.pt>
 */
public class FilterUtils {
  public static Filter filterByTable(Filter filter, String tableId) {
    Set<FilterParameter> otherTableFilters = new HashSet<>();
    boolean alreadyPresent = false;

    // look for other table filters, and note if one of them is the one we want
    for (FilterParameter filterParameter : filter.getParameters()) {
      if (filterParameter instanceof SimpleFilterParameter) {
        SimpleFilterParameter simpleFilterParameter = (SimpleFilterParameter) filterParameter;
        if (ViewerConstants.SOLR_ROWS_TABLE_ID.equals(simpleFilterParameter.getName())) {
          if (tableId.equals(simpleFilterParameter.getValue())) {
            alreadyPresent = true;
          } else {
            otherTableFilters.add(filterParameter);
          }
        }
      }
    }

    // remove other schemas filters. intersection between schemas is always empty
    for (FilterParameter otherTableFilter : otherTableFilters) {
      filter.getParameters().remove(otherTableFilter);
    }

    if (!alreadyPresent) {
      filter.add(new SimpleFilterParameter(ViewerConstants.SOLR_ROWS_TABLE_ID, tableId));
    }

    return filter;
  }
}

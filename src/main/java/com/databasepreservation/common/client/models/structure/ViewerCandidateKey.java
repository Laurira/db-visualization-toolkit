package com.databasepreservation.common.client.models.structure;

import java.io.Serializable;
import java.util.List;

/**
 * @author Bruno Ferreira <bferreira@keep.pt>
 */
public class ViewerCandidateKey implements Serializable {
  // mandatory in SIARD2
  private String name;
  private List<Integer> columnIndexesInViewerTable;

  // optional in SIARD2
  private String description;

  public ViewerCandidateKey() {
  }

  public List<Integer> getColumnIndexesInViewerTable() {
    return columnIndexesInViewerTable;
  }

  public void setColumnIndexesInViewerTable(List<Integer> columnIndexesInViewerTable) {
    this.columnIndexesInViewerTable = columnIndexesInViewerTable;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}

package com.databasepreservation.main.desktop.shared.models.wizardParameters;

import java.io.Serializable;

/**
 * @author Miguel Guimarães <mguimaraes@keep.pt>
 */
public class CustomViewsParameter implements Serializable {
  private String schema;
  private Integer customViewUUID;
  private String customViewName;
  private String customViewDescription;
  private String customViewQuery;

  public CustomViewsParameter() {
  }

  public CustomViewsParameter(Integer customViewUUID, String customViewName, String customViewDescription,
    String customViewQuery) {
    this.customViewUUID = customViewUUID;
    this.customViewName = customViewName;
    this.customViewDescription = customViewDescription;
    this.customViewQuery = customViewQuery;
  }

  public CustomViewsParameter(String schema, Integer customViewUUID, String customViewName,
    String customViewDescription, String customViewQuery) {
    this.schema = schema;
    this.customViewUUID = customViewUUID;
    this.customViewName = customViewName;
    this.customViewDescription = customViewDescription;
    this.customViewQuery = customViewQuery;
  }

  public String getSchema() {
    return schema;
  }

  public void setSchema(String schema) {
    this.schema = schema;
  }

  public Integer getCustomViewUUID() {
    return customViewUUID;
  }

  public void setCustomViewUUID(Integer customViewUUID) {
    this.customViewUUID = customViewUUID;
  }

  public String getCustomViewName() {
    return customViewName;
  }

  public void setCustomViewName(String customViewName) {
    this.customViewName = customViewName;
  }

  public String getCustomViewDescription() {
    return customViewDescription;
  }

  public void setCustomViewDescription(String customViewDescription) {
    this.customViewDescription = customViewDescription;
  }

  public String getCustomViewQuery() {
    return customViewQuery;
  }

  public void setCustomViewQuery(String customViewQuery) {
    this.customViewQuery = customViewQuery;
  }
}
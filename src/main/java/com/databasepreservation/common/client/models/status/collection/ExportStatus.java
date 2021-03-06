package com.databasepreservation.common.client.models.status.collection;

import java.io.Serializable;

/**
 * @author Gabriel Barros <gbarros@keep.pt>
 */
public class ExportStatus implements Serializable {
  private TemplateStatus templateStatus;

  public ExportStatus(){
  }

  public TemplateStatus getTemplateStatus() {
    return templateStatus;
  }

  public void setTemplateStatus(TemplateStatus templateStatus) {
    this.templateStatus = templateStatus;
  }
}

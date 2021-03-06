/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
/**
 * 
 */
package com.databasepreservation.common.client.common;

import com.databasepreservation.common.client.models.user.User;

/**
 * @author Luis Faria
 * 
 */
public interface LoginStatusListener {

  /**
   * Called when the status of the login changes
   * 
   * @param user
   */
  void onLoginStatusChanged(User user);

}

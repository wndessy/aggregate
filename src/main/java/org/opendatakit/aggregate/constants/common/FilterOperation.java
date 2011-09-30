/*
 * Copyright (C) 2011 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.aggregate.constants.common;

import java.io.Serializable;

/**
 * This enum defines the different filter operations which the Query can have.
 */
public enum FilterOperation implements Serializable {
 EQUAL("Equals"),
 GREATER_THAN("Greater Than"),
 GREATER_THAN_OR_EQUAL("Greater Than or Equal to"),
 LESS_THAN("Less Than"),
 LESS_THAN_OR_EQUAL("Less Than or Equal to");
 
 private String displayText;
 
 private FilterOperation(String display) {
   displayText = display;
 }
 
 public String getDisplayText() {
   return displayText;
 }
 
 public String toString() {
   return displayText;
 }
};
/*
 * Copyright 2016 Esri, Inc..
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.esri.geoportal.harvester.waf;

import java.util.List;

/**
 * WAF folder content
 */
/*package*/ class WafFolderContent {
  private final WafFolder rootFolder;
  private final List<WafFolder> subFolders;
  private final List<WafFile> files;

  /**
   * Creates instance of the folder content.
   * @param rootFolder root folder
   * @param subFolders sub folders
   * @param files files
   */
  public WafFolderContent(WafFolder rootFolder, List<WafFolder> subFolders, List<WafFile> files) {
    this.rootFolder = rootFolder;
    this.subFolders = subFolders;
    this.files = files;
  }

  /**
   * Gets root folder.
   * @return root folder
   */
  public WafFolder getRootFolder() {
    return rootFolder;
  }

  /**
   * Gets sub folders.
   * @return sub folders
   */
  public List<WafFolder> getSubFolders() {
    return subFolders;
  }

  /**
   * Gets files.
   * @return files
   */
  public List<WafFile> getFiles() {
    return files;
  }
  
  
}

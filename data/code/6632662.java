/*
 * Copyright 2016 Esri, Inc.
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
package com.esri.geoportal.harvester.api.base;

import com.esri.geoportal.harvester.api.DataReference;
import com.esri.geoportal.commons.constants.MimeType;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Data reference wrapper.
 */
public class DataReferenceWrapper implements DataReference {
  private static final long serialVersionUID = 1L;
  
  private final DataReference baseRef;
  
  // data
  private final Map<MimeType,byte []> content = new HashMap<>();
  
  /**
   * Creates instance of the data reference.
   * @param baseRef base data reference
   */
  public DataReferenceWrapper(DataReference baseRef) {
    this.baseRef = baseRef;
  }

  /**
   * Adds content of a particular type to the reference.
   * @param mimeType mime type
   * @param content content
   */
  public void addContext(MimeType mimeType, byte [] content) {
    this.content.put(mimeType, content);
  }
  

  @Override
  public byte[] getContent(MimeType...mimeType) throws IOException {
    byte [] data =  content.get(mimeType);
    if (data==null) {
      data = baseRef.getContent(mimeType);
    }
    return data;
  }

  @Override
  public Set<MimeType> getContentType() {
    HashSet<MimeType> mimeTypes = new HashSet<>();
    mimeTypes.addAll(content.keySet());
    mimeTypes.addAll(baseRef.getContentType());
    return mimeTypes;
  }

  @Override
  public String getId() {
    return baseRef.getId();
  }

  @Override
  public Date getLastModifiedDate() {
    return baseRef.getLastModifiedDate();
  }

  @Override
  public URI getSourceUri() {
    return baseRef.getSourceUri();
  }

  @Override
  public URI getBrokerUri() {
    return baseRef.getBrokerUri();
  }

  @Override
  public String getBrokerName() {
    return baseRef.getBrokerName();
  }

  @Override
  public Map<String, Object> getAttributesMap() {
    return baseRef.getAttributesMap();
  }

  @Override
  public DataReference getOriginDataReference() {
    return baseRef;
  }

  @Override
  public String getInputBrokerRef() {
    return baseRef.getInputBrokerRef();
  }

  @Override
  public String getTaskRef() {
    return baseRef.getTaskRef();
  }

  @Override
  public String toString() {
    return baseRef.toString();
  }
  
}

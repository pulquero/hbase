/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.rest.model;

import static org.apache.hadoop.hbase.rest.model.CellModel.MAGIC_LENGTH;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.Message;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.hadoop.hbase.rest.ProtobufMessageHandler;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.yetus.audience.InterfaceAudience;

/**
 * Representation of a row. A row is a related set of cells, grouped by common row key. RowModels do
 * not appear in results by themselves. They are always encapsulated within CellSetModels.
 *
 * <pre>
 * &lt;complexType name="Row"&gt;
 *   &lt;sequence&gt;
 *     &lt;element name="key" type="base64Binary"&gt;&lt;/element&gt;
 *     &lt;element name="cell" type="tns:Cell"
 *       maxOccurs="unbounded" minOccurs="1"&gt;&lt;/element&gt;
 *   &lt;/sequence&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlRootElement(name = "Row")
@XmlAccessorType(XmlAccessType.NONE)
@InterfaceAudience.Private
public class RowModel implements ProtobufMessageHandler, Serializable {
  private static final long serialVersionUID = 1L;

  // If keyLength = -1, this represents the key
  // If keyLength <> -1, this represents the base array, and key is determined by offset and length
  private byte[] key;

  private int keyOffset = 0;
  private int keyLength = MAGIC_LENGTH;

  @JsonProperty("Cell")
  @XmlElement(name = "Cell")
  private List<CellModel> cells = new ArrayList<>();

  /**
   * Default constructor
   */
  public RowModel() {
  }

  /**
   * Constructor
   * @param key the row key
   */
  public RowModel(final String key) {
    this(Bytes.toBytes(key));
  }

  /**
   * Constructor
   * @param key the row key
   */
  public RowModel(final byte[] key) {
    setKey(key);
    cells = new ArrayList<>();
  }

  /**
   * Constructor
   * @param key the row key as represented in the Cell
   */
  public RowModel(final byte[] key, int keyOffset, int keyLength) {
    this.key = key;
    this.keyOffset = keyOffset;
    this.keyLength = keyLength;
    cells = new ArrayList<>();
  }

  /**
   * Constructor
   * @param key   the row key
   * @param cells the cells
   */
  public RowModel(final String key, final List<CellModel> cells) {
    this(Bytes.toBytes(key), cells);
  }

  /**
   * Constructor
   * @param key   the row key
   * @param cells the cells
   */
  public RowModel(final byte[] key, final List<CellModel> cells) {
    this(key);
    this.cells = cells;
  }

  /**
   * Constructor
   * @param key   the row key
   * @param cells the cells
   */
  public RowModel(final byte[] key, int keyOffset, int keyLength, final List<CellModel> cells) {
    this(key, keyOffset, keyLength);
    this.cells = cells;
  }

  /**
   * Adds a cell to the list of cells for this row
   * @param cell the cell
   */
  public void addCell(CellModel cell) {
    cells.add(cell);
  }

  /** Returns the row key */
  @XmlAttribute
  @JsonProperty("key")
  public byte[] getKey() {
    if (keyLength == MAGIC_LENGTH) {
      return key;
    } else {
      byte[] retKey = new byte[keyLength];
      System.arraycopy(key, keyOffset, retKey, 0, keyLength);
      return retKey;
    }
  }

  /** Returns the backing row key array */
  public byte[] getKeyArray() {
    return key;
  }

  /**
   * @param key the row key
   */
  @JsonProperty("key")
  public void setKey(byte[] key) {
    this.key = key;
    this.keyLength = MAGIC_LENGTH;
  }

  public int getKeyOffset() {
    return keyOffset;
  }

  public int getKeyLength() {
    return keyLength;
  }

  /** Returns the cells */
  public List<CellModel> getCells() {
    return cells;
  }

  @Override
  public Message messageFromObject() {
    // there is no standalone row protobuf message
    throw new UnsupportedOperationException("no protobuf equivalent to RowModel");
  }

  @Override
  public ProtobufMessageHandler getObjectFromMessage(CodedInputStream is) throws IOException {
    // there is no standalone row protobuf message
    throw new UnsupportedOperationException("no protobuf equivalent to RowModel");
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    if (obj.getClass() != getClass()) {
      return false;
    }
    RowModel rowModel = (RowModel) obj;
    return new EqualsBuilder().append(getKey(), rowModel.getKey()).append(cells, rowModel.cells)
      .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(getKey()).append(cells).toHashCode();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this).append("key", getKey()).append("cells", cells).toString();
  }
}

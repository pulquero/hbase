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
package org.apache.hadoop.hbase.tool;

import static org.apache.hadoop.hbase.regionserver.storefiletracker.StoreFileTrackerFactory.TRACKER_IMPL;

import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.codec.KeyValueCodecWithTags;
import org.apache.hadoop.hbase.coprocessor.CoprocessorHost;
import org.apache.hadoop.hbase.regionserver.storefiletracker.StoreFileTrackerFactory;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.apache.hadoop.hbase.testclassification.MiscTests;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.experimental.categories.Category;

/**
 * Test cases for LoadIncrementalHFiles when SFT is enabled.
 */
@Category({ MiscTests.class, LargeTests.class })
public class TestLoadIncrementalHFilesSFT extends TestLoadIncrementalHFiles {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestLoadIncrementalHFilesSFT.class);

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    util.getConfiguration().set(CoprocessorHost.REGION_COPROCESSOR_CONF_KEY, "");
    util.getConfiguration().setInt(BulkLoadHFiles.MAX_FILES_PER_REGION_PER_FAMILY,
      MAX_FILES_PER_REGION_PER_FAMILY);
    util.getConfiguration().set(TRACKER_IMPL, StoreFileTrackerFactory.Trackers.FILE.name());
    // change default behavior so that tag values are returned with normal rpcs
    util.getConfiguration().set(HConstants.RPC_CODEC_CONF_KEY,
      KeyValueCodecWithTags.class.getCanonicalName());
    util.startMiniCluster();
    setupNamespace();
  }
}

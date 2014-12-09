/*
 * Copyright 2013-2014 eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kylinolap.cube;

import static org.junit.Assert.*;

import java.util.HashSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.kylinolap.common.util.JsonUtil;
import com.kylinolap.common.util.LocalFileMetadataTestCase;
import com.kylinolap.cube.model.CubeDesc;
import com.kylinolap.dict.Dictionary;
import com.kylinolap.dict.DictionaryInfo;
import com.kylinolap.dict.DictionaryManager;
import com.kylinolap.metadata.model.realization.TblColRef;

public class DictionaryManagerTest extends LocalFileMetadataTestCase {

    DictionaryManager dictMgr;

    @Before
    public void setup() throws Exception {
        createTestMetadata();
        dictMgr = DictionaryManager.getInstance(this.getTestConfig());
    }

    @After
    public void after() throws Exception {
        cleanupTestMetadata();
    }

    @Test
    @Ignore
    public void basic() throws Exception {
        CubeDesc cubeDesc = CubeDescManager.getInstance(this.getTestConfig()).getCubeDesc("test_kylin_cube_without_slr_desc");
        TblColRef col = cubeDesc.findColumnRef("TEST_SITES", "SITE_NAME");

        DictionaryInfo info1 = dictMgr.buildDictionary(cubeDesc.getModel(), cubeDesc.getRowkey().getDictionary(col), col, null);
        System.out.println(JsonUtil.writeValueAsIndentString(info1));

        DictionaryInfo info2 = dictMgr.buildDictionary(cubeDesc.getModel(), cubeDesc.getRowkey().getDictionary(col), col, null);
        System.out.println(JsonUtil.writeValueAsIndentString(info2));

        assertTrue(info1.getUuid() == info2.getUuid());

        assertTrue(info1 == dictMgr.getDictionaryInfo(info1.getResourcePath()));
        assertTrue(info2 == dictMgr.getDictionaryInfo(info2.getResourcePath()));

        assertTrue(info1.getDictionaryObject() == info2.getDictionaryObject());

        touchDictValues(info1);
    }

    @SuppressWarnings("unchecked")
    private void touchDictValues(DictionaryInfo info1) {
        Dictionary<String> dict = (Dictionary<String>) info1.getDictionaryObject();

        HashSet<String> set = new HashSet<String>();
        for (int i = 0, n = info1.getCardinality(); i < n; i++) {
            set.add(dict.getValueFromId(i));
        }
        assertEquals(info1.getCardinality(), set.size());
    }
}

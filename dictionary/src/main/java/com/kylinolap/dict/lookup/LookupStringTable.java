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

package com.kylinolap.dict.lookup;

import java.io.IOException;

import com.kylinolap.metadata.model.TableDesc;

/**
 * @author yangli9
 * 
 */
public class LookupStringTable extends LookupTable<String> {

    public LookupStringTable(TableDesc tableDesc, String[] keyColumns, ReadableTable table) throws IOException {
        super(tableDesc, keyColumns, table);
    }

    @Override
    protected String[] convertRow(String[] cols) {
        return cols;
    }

    @Override
    protected String toString(String cell) {
        return cell;
    }

}

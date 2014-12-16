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
package com.kylinolap.query.schema;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.kylinolap.cube.project.CubeRealizationManager;
import com.kylinolap.metadata.project.ProjectInstance;
import net.hydromatic.optiq.Schema;
import net.hydromatic.optiq.SchemaFactory;
import net.hydromatic.optiq.SchemaPlus;

import org.eigenbase.util14.ConversionUtil;

import com.kylinolap.common.KylinConfig;
import com.kylinolap.metadata.model.DatabaseDesc;
import com.kylinolap.metadata.model.TableDesc;

/**
 * @author xjiang
 */
public class OLAPSchemaFactory implements SchemaFactory {

    static {
        /*
         * Tricks Optiq to work with Unicode.
         * 
         * Sets default char set for string literals in SQL and row types of
         * RelNode. This is more a label used to compare row type equality. For
         * both SQL string and row record, they are passed to Optiq in String
         * object and does not require additional codec.
         * 
         * Ref SaffronProperties.defaultCharset
         * SqlUtil.translateCharacterSetName() NlsString constructor()
         */
        System.setProperty("saffron.default.charset", ConversionUtil.NATIVE_UTF16_CHARSET_NAME);
        System.setProperty("saffron.default.nationalcharset", ConversionUtil.NATIVE_UTF16_CHARSET_NAME);
        System.setProperty("saffron.default.collation.name", ConversionUtil.NATIVE_UTF16_CHARSET_NAME + "$en_US");
    }

    private final static String SCHEMA_PROJECT = "project";

    @Override
    public Schema create(SchemaPlus parentSchema, String schemaName, Map<String, Object> operand) {
        String project = (String) operand.get(SCHEMA_PROJECT);
        Schema newSchema = new OLAPSchema(project, schemaName);
        return newSchema;
    }

    public static File createTempOLAPJson(String project, KylinConfig config) {
        project = ProjectInstance.getNormalizedProjectName(project);

        List<TableDesc> tables = CubeRealizationManager.getInstance(config).listExposedTables(project);

        // "database" in TableDesc correspond to our schema
        // the logic to decide which schema to be "default" in calcite:
        // if some schema are named "default", use it.
        // other wise use the schema with most tables
        HashMap<String, Integer> schemaCounts = DatabaseDesc.extractDatabaseOccurenceCounts(tables);
        String majoritySchemaName = "";
        int majoritySchemaCount = 0;
        for (Map.Entry<String, Integer> e : schemaCounts.entrySet()) {
            if (e.getKey().equalsIgnoreCase("default")) {
                majoritySchemaCount = Integer.MAX_VALUE;
                majoritySchemaName = e.getKey();
            }

            if (e.getValue() >= majoritySchemaCount) {
                majoritySchemaCount = e.getValue();
                majoritySchemaName = e.getKey();
            }
        }

        try {
            File tmp = File.createTempFile("olap_model_", ".json");

            FileWriter out = new FileWriter(tmp);
            out.write("{\n");
            out.write("    \"version\": \"1.0\",\n");
            out.write("    \"defaultSchema\": \"" + majoritySchemaName + "\",\n");
            out.write("    \"schemas\": [\n");

            int counter = 0;
            for (String schemaName : schemaCounts.keySet()) {
                out.write("        {\n");
                out.write("            \"type\": \"custom\",\n");
                out.write("            \"name\": \"" + schemaName + "\",\n");
                out.write("            \"factory\": \"com.kylinolap.query.schema.OLAPSchemaFactory\",\n");
                out.write("            \"operand\": {\n");
                out.write("                \"" + SCHEMA_PROJECT + "\": \"" + project + "\"\n");
                out.write("            },\n");
                out.write("           \"functions\": [\n");
                out.write("                 {\n");
                out.write("                     \"name\": \"QUARTER\",\n");
                out.write("                     \"className\": \"com.kylinolap.query.sqlfunc.QuarterFunc\"\n");
                out.write("                 }\n");
                out.write("            ]\n");
                out.write("        }\n");

                if (++counter != schemaCounts.size()) {
                    out.write(",\n");
                }
            }

            out.write("    ]\n");
            out.write("}\n");
            out.close();

            tmp.deleteOnExit();
            return tmp;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

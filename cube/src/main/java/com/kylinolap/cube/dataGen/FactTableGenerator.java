package com.kylinolap.cube.dataGen;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;

import com.kylinolap.common.KylinConfig;
import com.kylinolap.common.persistence.ResourceStore;
import com.kylinolap.common.util.Array;
import com.kylinolap.cube.CubeInstance;
import com.kylinolap.cube.CubeManager;
import com.kylinolap.metadata.MetadataManager;
import com.kylinolap.metadata.model.cube.CubeDesc;
import com.kylinolap.metadata.model.cube.DimensionDesc;
import com.kylinolap.metadata.model.cube.JoinDesc;
import com.kylinolap.metadata.model.cube.MeasureDesc;
import com.kylinolap.metadata.model.cube.TblColRef;
import com.kylinolap.metadata.model.schema.ColumnDesc;
import com.kylinolap.metadata.model.schema.DataType;

/**
 * Created by hongbin on 5/20/14.
 */
public class FactTableGenerator {
    CubeInstance cube = null;
    CubeDesc desc = null;
    ResourceStore store = null;
    String factTableName = null;

    GenConfig genConf = null;

    Random r = null;

    String cubeName;
    long randomSeed;
    int rowCount;
    int unlinkableRowCount;
    int unlinkableRowCountMax;
    double conflictRatio;
    double linkableRatio;

    // the names of lookup table columns which is in relation with fact
    // table(appear as fk in fact table)
    Hashtable<String, LinkedList<String>> lookupTableKeys = new Hashtable<String, LinkedList<String>>();

    // possible values of lookupTableKeys, extracted from existing lookup
    // tables.
    // The key is in the format of tablename/columnname
    HashMap<String, ArrayList<String>> feasibleValues = new HashMap<String, ArrayList<String>>();

    // lookup table name -> sets of all composite keys
    HashMap<String, HashSet<Array<String>>> lookupTableCompositeKeyValues = new HashMap<String, HashSet<Array<String>>>();

    private void init(String cubeName, int rowCount, double conflictRaio, double linkableRatio, long randomSeed) {
        this.rowCount = rowCount;
        this.conflictRatio = conflictRaio;
        this.cubeName = cubeName;
        this.randomSeed = randomSeed;
        this.linkableRatio = linkableRatio;

        this.unlinkableRowCountMax = (int) (this.rowCount * (1 - linkableRatio));
        this.unlinkableRowCount = 0;

        r = new Random(randomSeed);

        KylinConfig config = KylinConfig.getInstanceFromEnv();
        cube = CubeManager.getInstance(config).getCube(cubeName);
        desc = cube.getDescriptor();
        factTableName = desc.getFactTable();
        store = ResourceStore.getStore(config);
    }

    /*
     * users can specify the value preference for each column
     */
    private void loadConfig() {
        try {
            InputStream configStream = null;
            configStream = store.getResource("/data/data_gen_config.json");
            this.genConf = GenConfig.loadConfig(configStream);

            if (configStream != null)
                configStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadLookupTableValues(String lookupTableName, LinkedList<String> columnNames, int distinctRowCount) throws Exception {
        KylinConfig config = KylinConfig.getInstanceFromEnv();

        // only deal with composite keys
        if (columnNames.size() > 1 && !lookupTableCompositeKeyValues.containsKey(lookupTableName)) {
            lookupTableCompositeKeyValues.put(lookupTableName, new HashSet<Array<String>>());
        }

        InputStream tableStream = null;
        BufferedReader tableReader = null;
        try {
            Hashtable<String, Integer> zeroBasedInice = new Hashtable<String, Integer>();
            for (String columnName : columnNames) {
                ColumnDesc cDesc = MetadataManager.getInstance(config).getTableDesc(lookupTableName).findColumnByName(columnName);
                zeroBasedInice.put(columnName, cDesc.getZeroBasedIndex());
            }

            String path = "/data/" + lookupTableName + ".csv";
            tableStream = store.getResource(path);
            tableReader = new BufferedReader(new InputStreamReader(tableStream));
            tableReader.mark(0);
            int rowCount = 0;
            int curRowNum = 0;
            String curRow;

            while (tableReader.readLine() != null)
                rowCount++;

            HashSet<Integer> rows = new HashSet<Integer>();
            distinctRowCount = (distinctRowCount < rowCount) ? distinctRowCount : rowCount;
            while (rows.size() < distinctRowCount) {
                rows.add(r.nextInt(rowCount));
            }

            // reopen the stream
            tableStream.close();
            tableReader.close();
            tableStream = null;
            tableReader = null;

            tableStream = store.getResource(path);
            tableReader = new BufferedReader(new InputStreamReader(tableStream));

            while ((curRow = tableReader.readLine()) != null) {
                if (rows.contains(curRowNum)) {
                    String[] tokens = curRow.split(",");

                    String[] comboKeys = null;
                    int index = 0;
                    if (columnNames.size() > 1)
                        comboKeys = new String[columnNames.size()];

                    for (String columnName : columnNames) {
                        int zeroBasedIndex = zeroBasedInice.get(columnName);
                        if (!feasibleValues.containsKey(lookupTableName + "/" + columnName))
                            feasibleValues.put(lookupTableName + "/" + columnName, new ArrayList<String>());
                        feasibleValues.get(lookupTableName + "/" + columnName).add(tokens[zeroBasedIndex]);

                        if (columnNames.size() > 1) {
                            comboKeys[index] = tokens[zeroBasedIndex];
                            index++;
                        }
                    }

                    if (columnNames.size() > 1) {
                        Array<String> wrap = new Array<String>(comboKeys);
                        if (lookupTableCompositeKeyValues.get(lookupTableName).contains(wrap)) {
                            throw new Exception("The composite key already exist in the lookup table");
                        }
                        lookupTableCompositeKeyValues.get(lookupTableName).add(wrap);
                    }
                }
                curRowNum++;
            }

            if (tableStream != null)
                tableStream.close();
            if (tableReader != null)
                tableReader.close();

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    // prepare the candidate values for each joined column
    private void prepare() throws Exception {
        // load config
        loadConfig();

        HashSet<String> factTableColumns = new HashSet<String>();

        for (DimensionDesc dim : desc.getDimensions()) {
            for (TblColRef col : dim.getColumnRefs()) {
                if (col.getTable().equals(factTableName))
                    factTableColumns.add(col.getName());
            }

            JoinDesc join = dim.getJoin();
            if (join != null) {
                String lookupTable = dim.getTable();
                for (String column : join.getPrimaryKey()) {
                    if (!lookupTableKeys.containsKey(lookupTable)) {
                        lookupTableKeys.put(lookupTable, new LinkedList<String>());
                    }

                    if (!lookupTableKeys.get(lookupTable).contains(column))
                        lookupTableKeys.get(lookupTable).add(column);
                }
            }
        }

        int distinctRowCount = (int) (this.rowCount / this.conflictRatio);
        distinctRowCount = (distinctRowCount == 0) ? 1 : distinctRowCount;
        // lookup tables
        for (String lookupTable : lookupTableKeys.keySet()) {
            this.loadLookupTableValues(lookupTable, lookupTableKeys.get(lookupTable), distinctRowCount);
        }
    }

    private List<DimensionDesc> getSortedDimentsionDescs() {
        List<DimensionDesc> dimensions = desc.getDimensions();
        Collections.sort(dimensions, new Comparator<DimensionDesc>() {
            @Override
            public int compare(DimensionDesc o1, DimensionDesc o2) {
                JoinDesc j1 = o2.getJoin();
                JoinDesc j2 = o1.getJoin();
                return Integer.valueOf(j1 != null ? j1.getPrimaryKey().length : 0).compareTo(j2 != null ? j2.getPrimaryKey().length : 0);
            }
        });
        return dimensions;
    }

    private void execute(String joinType) throws Exception {

        // main logic here , generate the data to a temp file
        String tempFilePath = generate();

        // Write to hbase
        File tempFile = new File(tempFilePath);

        InputStream in = new FileInputStream(tempFile);
        String factTablePath = "/data/" + factTableName + ".csv";
        store.deleteResource(factTablePath);
        store.putResource(factTablePath, in, System.currentTimeMillis());
        in.close();

        // duplicate a copy of this fact table, with a naming convention with
        // jointype added
        // so that later test cases can select different data files
        in = new FileInputStream(tempFile);
        String factTablePathWithJoinType = "/data/" + factTableName + ".csv." + joinType.toLowerCase();
        store.deleteResource(factTablePathWithJoinType);
        store.putResource(factTablePathWithJoinType, in, System.currentTimeMillis());
        in.close();

        tempFile.delete();

        System.out.println();
        System.out.println("The new fact table has been written to $KYLIN_METADATA_URL" + factTablePath);
        System.out.println();
    }

    /**
     * Generate the fact table and put it into a temp file
     *
     * @return
     * @throws Exception
     */
    private String generate() throws Exception {
        // the columns on the fact table can be classified into three groups:
        // 1. foreign keys
        HashMap<String, String> factTableCol2LookupCol = new HashMap<String, String>();
        // 2. metrics or directly used dimensions
        HashSet<String> usedCols = new HashSet<String>();
        // 3. others, not referenced anywhere

        HashMap<String, String> lookupCol2factTableCol = new HashMap<String, String>();

        // find fact table columns in fks
        List<DimensionDesc> dimensions = getSortedDimentsionDescs();
        for (DimensionDesc dim : dimensions) {
            JoinDesc jDesc = dim.getJoin();
            if (jDesc != null) {
                String[] fks = jDesc.getForeignKey();
                String[] pks = jDesc.getPrimaryKey();
                int num = fks.length;
                for (int i = 0; i < num; ++i) {
                    String value = dim.getTable() + "/" + pks[i];

                    lookupCol2factTableCol.put(value, fks[i]);

                    if (factTableCol2LookupCol.containsKey(fks[i])) {
                        if (!factTableCol2LookupCol.get(fks[i]).equals(value)) {
                            System.out.println("Warning: Disambiguation on the mapping of column " + fks[i] + ", " + factTableCol2LookupCol.get(fks[i]) + "(chosen) or " + value);
                            continue;
                        }
                    }
                    factTableCol2LookupCol.put(fks[i], value);
                }
            }
            //else, deal with it in next roung
        }

        // find fact table columns in direct dimension
        // DO NOT merge this with the previous loop
        for (DimensionDesc dim : dimensions) {
            JoinDesc jDesc = dim.getJoin();
            if (jDesc == null) {
                // column on fact table used directly as a dimension
                if (!factTableCol2LookupCol.containsKey(dim.getColumn()))
                    usedCols.add(dim.getColumn());
            }
        }

        // find fact table columns in measures
        for (MeasureDesc mDesc : desc.getMeasures()) {
            List<TblColRef> pcols = mDesc.getFunction().getParameter().getColRefs();
            if (pcols != null) {
                for (TblColRef col : pcols) {
                    if (!factTableCol2LookupCol.containsKey(col.getName()))
                        usedCols.add(col.getName());
                }
            }
        }

        return createTable(this.rowCount, factTableCol2LookupCol, lookupCol2factTableCol, usedCols);
    }

    private String normToTwoDigits(int v) {
        if (v < 10)
            return "0" + v;
        else
            return Integer.toString(v);
    }

    private String randomPick(ArrayList<String> candidates) {
        int index = r.nextInt(candidates.size());
        return candidates.get(index);
    }

    private String createRandomCell(ColumnDesc cDesc, ArrayList<String> range) throws Exception {
        DataType type = cDesc.getType();
        if (type.isStringFamily()) {
            throw new Exception("Can't handle range values for string");

        } else if (type.isIntegerFamily()) {
            int low = Integer.parseInt(range.get(0));
            int high = Integer.parseInt(range.get(1));
            return Integer.toString(r.nextInt(high - low) + low);

        } else if (type.isDouble()) {
            double low = Double.parseDouble(range.get(0));
            double high = Double.parseDouble(range.get(1));
            return String.format("%.4f", r.nextDouble() * (high - low) + low);

        } else if (type.isFloat()) {
            float low = Float.parseFloat(range.get(0));
            float high = Float.parseFloat(range.get(1));
            return String.format("%.4f", r.nextFloat() * (high - low) + low);

        } else if (type.isDecimal()) {
            double low = Double.parseDouble(range.get(0));
            double high = Double.parseDouble(range.get(1));
            return String.format("%.4f", r.nextDouble() * (high - low) + low);

        } else if (type.isDateTimeFamily()) {

            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            Date start = format.parse(range.get(0));
            Date end = format.parse(range.get(1));
            long diff = end.getTime() - start.getTime();
            Date temp = new Date(start.getTime() + (long) (diff * r.nextDouble()));
            Calendar cal = Calendar.getInstance();
            cal.setTime(temp);
            // first day
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());

            return cal.get(Calendar.YEAR) + "-" + normToTwoDigits(cal.get(Calendar.MONTH) + 1) + "-" + normToTwoDigits(cal.get(Calendar.DAY_OF_MONTH));
        } else {
            System.out.println("The data type " + type + "is not recognized");
            System.exit(1);
        }
        return null;
    }

    private String createRandomCell(ColumnDesc cDesc) {
        String type = cDesc.getTypeName();
        String s = type.toLowerCase();
        if (s.equals("string") || s.equals("char") || s.equals("varchar")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 2; i++) {
                sb.append((char) ('a' + r.nextInt(10)));// there are 10*10
                // possible strings
            }
            return sb.toString();
        } else if (s.equals("bigint") || s.equals("int") || s.equals("tinyint") || s.equals("smallint")) {
            return Integer.toString(r.nextInt(128));
        } else if (s.equals("double")) {
            return String.format("%.4f", r.nextDouble() * 100);
        } else if (s.equals("float")) {
            return String.format("%.4f", r.nextFloat() * 100);
        } else if (s.equals("decimal")) {
            return String.format("%.4f", r.nextDouble() * 100);
        } else if (s.equals("date")) {
            long date20131231 = 61349312153265L;
            long date20010101 = 60939158400000L;
            long diff = date20131231 - date20010101;
            Date temp = new Date(date20010101 + (long) (diff * r.nextDouble()));
            Calendar cal = Calendar.getInstance();
            cal.setTime(temp);
            // first day
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());

            return cal.get(Calendar.YEAR) + "-" + normToTwoDigits(cal.get(Calendar.MONTH) + 1) + "-" + normToTwoDigits(cal.get(Calendar.DAY_OF_MONTH));
        } else {
            System.out.println("The data type " + type + "is not recognized");
            System.exit(1);
        }
        return null;
    }

    private String createDefaultsCell(String type) {
        String s = type.toLowerCase();
        if (s.equals("string") || s.equals("char") || s.equals("varchar")) {
            return "abcde";
        } else if (s.equals("bigint") || s.equals("int") || s.equals("tinyint") || s.equals("smallint")) {
            return "0";
        } else if (s.equals("double")) {
            return "0";
        } else if (s.equals("float")) {
            return "0";
        } else if (s.equals("decimal")) {
            return "0";
        } else if (s.equals("date")) {
            return "1970-01-01";
        } else {
            System.out.println("The data type " + type + "is not recognized");
            System.exit(1);
        }
        return null;
    }

    private void printColumnMappings(HashMap<String, String> factTableCol2LookupCol, HashSet<String> usedCols, HashSet<String> defaultColumns) {

        System.out.println("=======================================================================");
        System.out.format("%-30s %s", "FACT_TABLE_COLUMN", "MAPPING");
        System.out.println();
        System.out.println();
        for (Map.Entry<String, String> entry : factTableCol2LookupCol.entrySet()) {
            System.out.format("%-30s %s", entry.getKey(), entry.getValue());
            System.out.println();
        }
        for (String key : usedCols) {
            System.out.format("%-30s %s", key, "Random Values");
            System.out.println();
        }
        for (String key : defaultColumns) {
            System.out.format("%-30s %s", key, "Default Values");
            System.out.println();
        }
        System.out.println("=======================================================================");

        System.out.println("Parameters:");
        System.out.println();
        System.out.println("CubeName:        " + cubeName);
        System.out.println("RowCount:        " + rowCount);
        System.out.println("ConflictRatio:   " + conflictRatio);
        System.out.println("LinkableRatio:   " + linkableRatio);
        System.out.println("Seed:            " + randomSeed);
        System.out.println();
        System.out.println("The number of actual unlinkable fact rows is: " + this.unlinkableRowCount);
        System.out.println("You can vary the above parameters to generate different datasets.");
        System.out.println();
    }

    // Any row in the column must finally appear in the flatten big table.
    // for single-column joins the generated row is guaranteed to have a match
    // in lookup table
    // for composite keys we'll need an extra check
    private boolean matchAllCompositeKeys(HashMap<String, String> lookupCol2FactTableCol, LinkedList<String> columnValues) {
        KylinConfig config = KylinConfig.getInstanceFromEnv();

        for (String lookupTable : lookupTableKeys.keySet()) {
            if (lookupTableKeys.get(lookupTable).size() == 1)
                continue;

            String[] comboKey = new String[lookupTableKeys.get(lookupTable).size()];
            int index = 0;
            for (String column : lookupTableKeys.get(lookupTable)) {
                String key = lookupTable + "/" + column;
                String factTableCol = lookupCol2FactTableCol.get(key);
                int cardinal = MetadataManager.getInstance(config).getTableDesc(factTableName).findColumnByName(factTableCol).getZeroBasedIndex();
                comboKey[index] = columnValues.get(cardinal);

                index++;
            }
            Array<String> wrap = new Array<String>(comboKey);
            if (!lookupTableCompositeKeyValues.get(lookupTable).contains(wrap)) {
                // System.out.println("Try " + wrap + " Failed, continue...");
                return false;
            }
        }
        return true;
    }

    private String createCell(ColumnDesc cDesc) throws Exception {
        ColumnConfig cConfig = null;

        if ((cConfig = genConf.getColumnConfigByName(cDesc.getName())) == null) {
            // if the column is not configured, use random values
            return (createRandomCell(cDesc));

        } else {
            // the column has a configuration
            if (!cConfig.isAsRange() && !cConfig.isExclusive() && r.nextBoolean()) {
                // if the column still allows random values
                return (createRandomCell(cDesc));

            } else {
                // use specified values
                ArrayList<String> valueSet = cConfig.getValueSet();
                if (valueSet == null || valueSet.size() == 0)
                    throw new Exception("Did you forget to specify value set for " + cDesc.getName());

                if (!cConfig.isAsRange()) {
                    return (randomPick(valueSet));
                } else {
                    if (valueSet.size() != 2)
                        throw new Exception("Only two values can be set for range values, the column: " + cDesc.getName());

                    return (createRandomCell(cDesc, valueSet));
                }
            }

        }
    }

    private LinkedList<String> createRow(HashMap<String, String> factTableCol2LookupCol, HashSet<String> usedCols, HashSet<String> defaultColumns) throws Exception {
        KylinConfig config = KylinConfig.getInstanceFromEnv();
        LinkedList<String> columnValues = new LinkedList<String>();

        for (ColumnDesc cDesc : MetadataManager.getInstance(config).getTableDesc(factTableName).getColumns()) {


            String colName = cDesc.getName();

            if (factTableCol2LookupCol.containsKey(colName)) {

                // if the current column is a fk column in fact table
                ArrayList<String> candidates = this.feasibleValues.get(factTableCol2LookupCol.get(colName));

                columnValues.add(candidates.get(r.nextInt(candidates.size())));
            } else if (usedCols.contains(colName)) {

                // if the current column is a metric column in fact table
                columnValues.add(createCell(cDesc));
            } else {

                // otherwise this column is not useful in OLAP
                columnValues.add(createDefaultsCell(cDesc.getTypeName()));
                defaultColumns.add(colName);
            }
        }

        return columnValues;
    }

    private String createTable(int rowCount, HashMap<String, String> factTableCol2LookupCol, HashMap<String, String> lookupCol2FactTableCol, HashSet<String> usedCols) throws Exception {
        try {
            File tempFile = File.createTempFile("ftg", ".tmp");
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));
            HashSet<String> defaultColumns = new HashSet<String>();

            for (int i = 0; i < rowCount; ) {

                LinkedList<String> columnValues = createRow(factTableCol2LookupCol, usedCols, defaultColumns);

                if (!matchAllCompositeKeys(lookupCol2FactTableCol, columnValues)) {
                    if (unlinkableRowCount < unlinkableRowCountMax) {
                        unlinkableRowCount++;
                    } else {
                        continue;
                    }
                }

                StringBuffer sb = new StringBuffer();
                for (String c : columnValues)
                    sb.append(c + ",");
                sb.deleteCharAt(sb.length() - 1);
                writer.write(sb.toString());
                writer.newLine();
                i++;

                // System.out.println("Just generated the " + i + "th record");
            }
            writer.flush();
            writer.close();

            printColumnMappings(factTableCol2LookupCol, usedCols, defaultColumns);

            return tempFile.getAbsolutePath();

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        return null;
    }

    /**
     * Randomly create a fact table and put it to test_kylin_data table in hbase
     *
     * @param cubeName      name of the cube
     * @param rowCount      expected row count generated
     * @param linkableRatio the percentage of fact table rows that can be linked with all
     *                      lookup table by INNER join
     * @param randomSeed    random seed
     */
    public static void generate(String cubeName, String rowCount, String linkableRatio, String randomSeed, String joinType) throws Exception {

        if (cubeName == null)
            cubeName = "test_kylin_cube_with_slr_ready";
        if (rowCount == null)
            rowCount = "10000";
        if (linkableRatio == null)
            linkableRatio = "0.6";

        //if (randomSeed == null)
        // don't give it value

        // String conflictRatio = "5";//this parameter do not allow configuring
        // any more

        FactTableGenerator generator = new FactTableGenerator();
        long seed;
        if (randomSeed != null) {
            seed = Long.parseLong(randomSeed);
        } else {
            Random r = new Random();
            seed = r.nextLong();
        }

        generator.init(cubeName, Integer.parseInt(rowCount), 5, Double.parseDouble(linkableRatio), seed);
        generator.prepare();
        generator.execute(joinType);
    }
}

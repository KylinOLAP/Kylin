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

package com.kylinolap.jdbc.stub;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import net.hydromatic.avatica.AvaticaStatement;
import net.hydromatic.avatica.ColumnMetaData;
import net.hydromatic.avatica.ColumnMetaData.Rep;
import net.hydromatic.avatica.ColumnMetaData.ScalarType;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashMultimap;
import com.kylinolap.jdbc.KylinConnectionImpl;
import com.kylinolap.jdbc.KylinEnumerator;
import com.kylinolap.jdbc.KylinJdbc41Factory.KylinJdbc41PreparedStatement;
import com.kylinolap.jdbc.KylinMetaImpl.MetaCatalog;
import com.kylinolap.jdbc.KylinMetaImpl.MetaColumn;
import com.kylinolap.jdbc.KylinMetaImpl.MetaProject;
import com.kylinolap.jdbc.KylinMetaImpl.MetaSchema;
import com.kylinolap.jdbc.KylinMetaImpl.MetaTable;
import com.kylinolap.jdbc.stub.TableMetaStub.ColumnMetaStub;
import com.kylinolap.jdbc.util.DefaultSslProtocolSocketFactory;
import com.kylinolap.jdbc.util.SQLTypeMap;

/**
 * @author xduo
 * 
 */
public class KylinClient implements RemoteClient {
    private static final Logger logger = LoggerFactory.getLogger(KylinClient.class);

    private final KylinConnectionImpl conn;

    public KylinClient(KylinConnectionImpl conn) {
        this.conn = conn;
    }

    @Override
    public void connect() throws ConnectionException {
        PostMethod post = new PostMethod(conn.getConnectUrl());
        HttpClient httpClient = new HttpClient();

        if (conn.getConnectUrl().toLowerCase().startsWith("https://")) {
            registerSsl();
        }
        addPostHeaders(post);

        try {
            StringRequestEntity requestEntity = new StringRequestEntity("{}", "application/json", "UTF-8");
            post.setRequestEntity(requestEntity);
            httpClient.executeMethod(post);

            if (post.getStatusCode() != 200 && post.getStatusCode() != 201) {
                logger.error("Authentication Failed with error code " + post.getStatusCode() + " and message:\n" + post.getResponseBodyAsString());

                throw new ConnectionException("Authentication Failed.");
            }
        } catch (HttpException e) {
            logger.error(e.getLocalizedMessage(), e);
            throw new ConnectionException(e.getLocalizedMessage());
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
            throw new ConnectionException(e.getLocalizedMessage());
        }
    }

    @Override
    public MetaProject getMetadata(String project) throws ConnectionException {
        GetMethod get = new GetMethod(conn.getMetaProjectUrl(project));
        HttpClient httpClient = new HttpClient();

        if (conn.getConnectUrl().toLowerCase().startsWith("https://")) {
            registerSsl();
        }
        addPostHeaders(get);

        List<TableMetaStub> tableMetaStubs = null;
        try {
            httpClient.executeMethod(get);

            if (get.getStatusCode() != 200 && get.getStatusCode() != 201) {
                logger.error("Authentication Failed with error code " + get.getStatusCode() + " and message:\n" + get.getResponseBodyAsString());

                throw new ConnectionException("Authentication Failed.");
            }

            tableMetaStubs = new ObjectMapper().readValue(get.getResponseBodyAsString(), new TypeReference<List<TableMetaStub>>() {
            });

            List<MetaTable> tables = new ArrayList<MetaTable>();
            HashMultimap<String, MetaTable> schemasMap = HashMultimap.create();

            for (TableMetaStub tableMetaStub : tableMetaStubs) {
                List<MetaColumn> columns = new ArrayList<MetaColumn>();

                for (ColumnMetaStub columnMetaStub : tableMetaStub.getColumns()) {
                    MetaColumn column = createNewColumn(columnMetaStub);
                    columns.add(column);
                }

                MetaTable table = createNewTable(tableMetaStub, columns);
                tables.add(table);
                schemasMap.put(tableMetaStub.getTABLE_CAT() + "#" + tableMetaStub.getTABLE_SCHEM(), table);
            }

            HashMultimap<String, MetaSchema> catalogMap = HashMultimap.create();
            List<MetaSchema> schemas = new ArrayList<MetaSchema>();
            for (String key : schemasMap.keySet()) {
                String cat = key.split("#")[0];
                String schema = key.split("#")[1];
                MetaSchema metaSchema = new MetaSchema(cat, schema, new ArrayList<MetaTable>(schemasMap.get(key)));
                schemas.add(metaSchema);
                catalogMap.put(cat, metaSchema);
            }

            List<MetaCatalog> catalogs = new ArrayList<MetaCatalog>();
            for (String key : catalogMap.keySet()) {
                MetaCatalog metaCatalog = new MetaCatalog(key, new ArrayList<MetaSchema>(catalogMap.get(key)));
                catalogs.add(metaCatalog);
            }

            return new MetaProject(project, catalogs);
        } catch (HttpException e) {
            logger.error(e.getLocalizedMessage(), e);
            throw new ConnectionException(e.getLocalizedMessage());
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
            throw new ConnectionException(e.getLocalizedMessage());
        }
    }

    private MetaTable createNewTable(TableMetaStub tableMetaStub, List<MetaColumn> columns) {
        MetaTable table = new MetaTable(tableMetaStub.getTABLE_CAT(), tableMetaStub.getTABLE_SCHEM(), tableMetaStub.getTABLE_NAME(), tableMetaStub.getTABLE_TYPE(), tableMetaStub.getREMARKS(), tableMetaStub.getTYPE_CAT(), tableMetaStub.getTYPE_SCHEM(), tableMetaStub.getTYPE_NAME(), tableMetaStub.getSELF_REFERENCING_COL_NAME(), tableMetaStub.getREF_GENERATION(), columns);
        return table;
    }

    private MetaColumn createNewColumn(ColumnMetaStub columnMetaStub) {
        MetaColumn column = new MetaColumn(columnMetaStub.getTABLE_CAT(), columnMetaStub.getTABLE_SCHEM(), columnMetaStub.getTABLE_NAME(), columnMetaStub.getCOLUMN_NAME(), columnMetaStub.getDATA_TYPE(), columnMetaStub.getTYPE_NAME(), columnMetaStub.getCOLUMN_SIZE(), columnMetaStub.getBUFFER_LENGTH(), columnMetaStub.getDECIMAL_DIGITS(), columnMetaStub.getNUM_PREC_RADIX(), columnMetaStub.getNULLABLE(), columnMetaStub.getREMARKS(), columnMetaStub.getCOLUMN_DEF(), columnMetaStub.getSQL_DATA_TYPE(), columnMetaStub.getSQL_DATETIME_SUB(), columnMetaStub.getCHAR_OCTET_LENGTH(), columnMetaStub.getORDINAL_POSITION(), columnMetaStub.getIS_NULLABLE(), columnMetaStub.getSCOPE_CATLOG(), columnMetaStub.getSCOPE_TABLE(), columnMetaStub.getSOURCE_DATA_TYPE(), columnMetaStub.getIS_AUTOINCREMENT(), columnMetaStub.getSCOPE_SCHEMA());
        return column;
    }

    @Override
    public DataSet<Object[]> query(AvaticaStatement statement, String sql) throws SQLException {
        SQLResponseStub queryRes = null;

        List<StateParam> params = null;
        if (statement instanceof KylinJdbc41PreparedStatement) {
            params = genPrestateStates(statement);
        }

        queryRes = runKylinQuery(sql, params);

        List<ColumnMetaData> metas = genColumnMeta(queryRes);
        List<Object[]> data = genResultData(queryRes, metas);

        return new DataSet<Object[]>(metas, new KylinEnumerator<Object[]>(data));
    }

    /**
     * @param queryRes
     * @param metas
     * @return
     */
    private List<Object[]> genResultData(SQLResponseStub queryRes, List<ColumnMetaData> metas) {
        List<Object[]> data = new ArrayList<Object[]>();
        for (String[] result : queryRes.getResults()) {
            Object[] row = new Object[result.length];

            for (int i = 0; i < result.length; i++) {
                ColumnMetaData meta = metas.get(i);
                row[i] = SQLTypeMap.wrapObject(result[i], meta.type.type);
            }

            data.add(row);
        }
        return data;
    }

    /**
     * @param statement
     * @param params
     */
    private List<StateParam> genPrestateStates(AvaticaStatement statement) {
        List<StateParam> params = new ArrayList<StateParam>();
        List<Object> values = ((KylinJdbc41PreparedStatement) statement).getParameterValues();

        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            params.add(new StateParam(value.getClass().getCanonicalName(), String.valueOf(value)));
        }

        return params;
    }

    /**
     * @param queryRes
     * @return
     */
    private List<ColumnMetaData> genColumnMeta(SQLResponseStub queryRes) {
        List<ColumnMetaData> metas = new ArrayList<ColumnMetaData>();
        for (int i = 0; i < queryRes.getColumnMetas().size(); i++) {
            SQLResponseStub.ColumnMetaStub scm = queryRes.getColumnMetas().get(i);
            ScalarType type = ColumnMetaData.scalar(scm.getColumnType(), scm.getColumnTypeName(), Rep.of(SQLTypeMap.convert(scm.getColumnType())));

            ColumnMetaData meta = new ColumnMetaData(i, scm.isAutoIncrement(), scm.isCaseSensitive(), scm.isSearchable(), scm.isCurrency(), scm.getIsNullable(), scm.isSigned(), scm.getDisplaySize(), scm.getLabel(), scm.getName(), scm.getSchemaName(), scm.getPrecision(), scm.getScale(), scm.getTableName(), scm.getSchemaName(), type, scm.isReadOnly(), scm.isWritable(), scm.isWritable(), null);

            metas.add(meta);
        }

        return metas;
    }

    /**
     * @param sql
     * @return
     * @throws IOException
     */
    private SQLResponseStub runKylinQuery(String sql, List<StateParam> params) throws SQLException {
        String url = conn.getQueryUrl();
        String project = conn.getProject();
        QueryRequest request = null;

        if (null != params) {
            request = new PreQueryRequest();
            ((PreQueryRequest) request).setParams(params);
            url += "/prestate";
        } else {
            request = new QueryRequest();
        }
        request.setSql(sql);
        request.setProject(project);

        PostMethod post = new PostMethod(url);
        addPostHeaders(post);
        HttpClient httpClient = new HttpClient();
        if (conn.getQueryUrl().toLowerCase().startsWith("https://")) {
            registerSsl();
        }

        String postBody = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            postBody = mapper.writeValueAsString(request);
            logger.debug("Post body:\n " + postBody);
        } catch (JsonProcessingException e) {
            logger.error(e.getLocalizedMessage(), e);
        }
        String response = null;
        SQLResponseStub queryRes = null;

        try {
            StringRequestEntity requestEntity = new StringRequestEntity(postBody, "application/json", "UTF-8");
            post.setRequestEntity(requestEntity);

            httpClient.executeMethod(post);
            response = post.getResponseBodyAsString();

            if (post.getStatusCode() != 200 && post.getStatusCode() != 201) {
                logger.error("Failed to query", response);
                throw new SQLException(response);
            }

            queryRes = new ObjectMapper().readValue(response, SQLResponseStub.class);

        } catch (HttpException e) {
            logger.error(e.getLocalizedMessage(), e);
            throw new SQLException(e.getLocalizedMessage());
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
            throw new SQLException(e.getLocalizedMessage());
        }

        return queryRes;
    }

    private void addPostHeaders(HttpMethodBase method) {
        method.getParams().setContentCharset(this.conn.getAllInfo().getProperty("characterEncoding", "ISO-8859-1"));
        method.addRequestHeader("Accept", "application/json, text/plain, */*");
        method.addRequestHeader("Content-Type", "application/json");
        method.addRequestHeader("Authorization", "Basic " + conn.getBasicAuthHeader());
    }

    private void registerSsl() {
        Protocol.registerProtocol("https", new Protocol("https", (ProtocolSocketFactory) new DefaultSslProtocolSocketFactory(), 443));
    }

    public class QueryRequest {
        private String sql;
        private String project;

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public String getProject() {
            return project;
        }

        public void setProject(String project) {
            this.project = project;
        }
    }

    public class PreQueryRequest extends QueryRequest {
        private List<StateParam> params;

        public List<StateParam> getParams() {
            return params;
        }

        public void setParams(List<StateParam> params) {
            this.params = params;
        }
    }

    public class StateParam {
        private String className;
        private String value;

        public StateParam(String className, String value) {
            super();
            this.className = className;
            this.value = value;
        }

        public String getClassName() {
            return className;
        }

        public void setClazz(String className) {
            this.className = className;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}

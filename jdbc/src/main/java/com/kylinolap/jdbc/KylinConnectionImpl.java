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

package com.kylinolap.jdbc;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.xml.bind.DatatypeConverter;

import net.hydromatic.avatica.AvaticaConnection;
import net.hydromatic.avatica.AvaticaFactory;
import net.hydromatic.avatica.AvaticaPreparedStatement;
import net.hydromatic.avatica.AvaticaStatement;
import net.hydromatic.avatica.Meta;
import net.hydromatic.avatica.UnregisteredDriver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kylinolap.jdbc.KylinMetaImpl.MetaProject;
import com.kylinolap.jdbc.KylinPrepare.PrepareResult;

/**
 * Kylin connection implementation
 * 
 * @author xduo
 * 
 */
public abstract class KylinConnectionImpl extends AvaticaConnection {
    private static final Logger logger = LoggerFactory.getLogger(KylinConnectionImpl.class);

    private final String baseUrl;
    private final String project;
    private MetaProject metaProject;
    public final List<AvaticaStatement> statements;
    static final Trojan TROJAN = createTrojan();
    private Properties allInfo = new Properties();
    
    protected KylinConnectionImpl(UnregisteredDriver driver, AvaticaFactory factory, String url, Properties info) {
        super(driver, factory, url, info);

        allInfo.putAll(this.info);
        
        String odbcUrl = url;
        String keyValues = "";
        odbcUrl = odbcUrl.replace(Driver.CONNECT_STRING_PREFIX + "//", "");
        
        int idx = odbcUrl.indexOf("?");
        if ( idx != -1){
            
            keyValues = odbcUrl.substring(idx + 1);
            odbcUrl = odbcUrl.substring(0, idx);
            
            StringTokenizer queryParams = new StringTokenizer(keyValues, "&"); 
            while (queryParams.hasMoreTokens()) {
                
                String parameterValuePair = queryParams.nextToken();

                int indexOfEquals = parameterValuePair.indexOf("=");

                String parameter = null;
                String value = null;

                if (indexOfEquals != -1) {
                    parameter = parameterValuePair.substring(0, indexOfEquals);

                    if (indexOfEquals + 1 < parameterValuePair.length()) {
                        value = parameterValuePair.substring(indexOfEquals + 1);
                    }
                }

                if ((value != null && value.length() > 0)
                        && (parameter != null && parameter.length() > 0)) {
                    try {
                        allInfo.put(parameter, URLDecoder.decode(value,
                                "UTF-8"));
                    } catch (UnsupportedEncodingException badEncoding) {
                        // punt
                        allInfo.put(parameter, URLDecoder.decode(value));
                    } catch (NoSuchMethodError nsme) {
                        // punt again
                        allInfo.put(parameter, URLDecoder.decode(value));
                    }
                }
            }
        }
        
        String[] temps = odbcUrl.split("/");

        assert temps.length == 2;

        this.baseUrl = temps[0];
        this.project = temps[1];

        logger.debug("Kylin base url " + this.baseUrl + ", project name " + this.project);

        statements = new ArrayList<AvaticaStatement>();
    }

    @Override
    protected Meta createMeta() {
        return new KylinMetaImpl(this, (KylinJdbc41Factory) factory);
    }

    @Override
    public AvaticaStatement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        AvaticaStatement statement = super.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
        statements.add(statement);

        return statement;
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        PrepareResult pr = new KylinPrepareImpl().prepare(sql);
        AvaticaPreparedStatement statement = ((KylinJdbc41Factory) factory).newPreparedStatement(this, pr, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, this.getHoldability());
        statements.add(statement);

        return statement;
    }

    // ~ kylin specified implements

    public String getBasicAuthHeader() {
        String username = this.info.getProperty("user");
        String password = this.info.getProperty("password");

        return DatatypeConverter.printBase64Binary((username + ":" + password).getBytes());
    }

    public String getConnectUrl() {
        boolean isSsl = Boolean.parseBoolean((this.info.getProperty("ssl", "false")));
        return (isSsl ? "https://" : "http://") + this.baseUrl + ":" + (isSsl ? 443 : 80) + "/kylin/api/user/authentication";
    }

    public String getMetaProjectUrl(String project) {
        assert project != null;
        boolean isSsl = Boolean.parseBoolean((this.info.getProperty("ssl", "false")));
        return (isSsl ? "https://" : "http://") + this.baseUrl + ":" + (isSsl ? 443 : 80) + "/kylin/api/tables_and_columns?project=" + project;
    }

    public String getQueryUrl() {
        boolean isSsl = Boolean.parseBoolean((this.info.getProperty("ssl", "false")));
        return (isSsl ? "https://" : "http://") + this.baseUrl + ":" + (isSsl ? 443 : 80) + "/kylin/api/query";
    }

    public String getProject() {
        return this.project;
    }

    public Meta getMeta() {
        return this.meta;
    }

    public AvaticaFactory getFactory() {
        return this.factory;
    }

    public UnregisteredDriver getDriver() {
        return this.driver;
    }

    public MetaProject getMetaProject() {
        return metaProject;
    }

    public void setMetaProject(MetaProject metaProject) {
        this.metaProject = metaProject;
    }

    public Properties getAllInfo() {
        return allInfo;
    }

    public void setAllInfo(Properties allInfo) {
        this.allInfo = allInfo;
    }
    
    @Override
    public void close() throws SQLException {
        super.close();

        this.metaProject = null;
        this.statements.clear();
    }

    @Override
    public String toString() {
        return "KylinConnectionImpl [baseUrl=" + baseUrl + ", project=" + project + ", metaProject=" + metaProject + "]";
    }

}

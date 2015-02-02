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

import java.sql.SQLException;

import net.hydromatic.avatica.AvaticaConnection;
import net.hydromatic.avatica.AvaticaStatement;
import net.hydromatic.avatica.DriverVersion;
import net.hydromatic.avatica.Handler;
import net.hydromatic.avatica.HandlerImpl;
import net.hydromatic.avatica.UnregisteredDriver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kylinolap.jdbc.stub.ConnectionException;
import com.kylinolap.jdbc.stub.RemoteClient;

/**
 * <p>
 * Kylin JDBC Driver based on optiq avatica and kylin restful api.<br>
 * Supported versions:
 * </p>
 * <ul>
 * <li>jdbc 4.0</li>
 * <li>jdbc 4.1</li>
 * </ul>
 * 
 * <p>
 * Supported Statements:
 * </p>
 * <ul>
 * <li>{@link KylinStatementImpl}</li>
 * <li>{@link KylinPrepareStatementImpl}</li>
 * </ul>
 * 
 * <p>
 * Supported properties:
 * <ul>
 * <li>user: username</li>
 * <li>password: password</li>
 * <li>ssl: true/false</li>
 * </ul>
 * </p>
 * 
 * <p>
 * Driver init code sample:<br>
 * 
 * <pre>
 * Driver driver = (Driver) Class.forName(&quot;com.kylinolap.kylin.jdbc.Driver&quot;).newInstance();
 * Properties info = new Properties();
 * info.put(&quot;user&quot;, &quot;user&quot;);
 * info.put(&quot;password&quot;, &quot;password&quot;);
 * info.put(&quot;ssl&quot;, true);
 * Connection conn = driver.connect(&quot;jdbc:kylin://{domain}/{project}&quot;, info);
 * </pre>
 * 
 * </p>
 * 
 * @author xduo
 * 
 */
public class Driver extends UnregisteredDriver {
    private static final Logger logger = LoggerFactory.getLogger(Driver.class);

    public static final String CONNECT_STRING_PREFIX = "jdbc:kylin:";

    @Override
    protected DriverVersion createDriverVersion() {
        return DriverVersion.load(Driver.class, "com-kylinolap-kylin-jdbc.properties", "Kylin JDBC Driver", "unknown version", "Kylin", "unknown version");
    }

    @Override
    protected String getFactoryClassName(JdbcVersion jdbcVersion) {
        switch (jdbcVersion) {
        case JDBC_30:
            throw new UnsupportedOperationException();
        case JDBC_40:
            return "com.kylinolap.jdbc.KylinJdbc40Factory";
        case JDBC_41:
        default:
            return "com.kylinolap.jdbc.KylinJdbc41Factory";
        }
    }

    @Override
    protected Handler createHandler() {
        return new HandlerImpl() {
            @Override
            public void onConnectionInit(AvaticaConnection connection_) throws SQLException {
                KylinConnectionImpl kylinConn = (KylinConnectionImpl) connection_;
                RemoteClient runner = ((KylinJdbc41Factory) factory).newRemoteClient(kylinConn);
                try {
                    runner.connect();
                    kylinConn.setMetaProject(runner.getMetadata(kylinConn.getProject()));
                    logger.debug("Connection inited.");
                } catch (ConnectionException e) {
                    logger.error(e.getLocalizedMessage(), e);
                    throw new SQLException(e.getLocalizedMessage());
                }
            }

            public void onConnectionClose(AvaticaConnection connection) {
                logger.debug("Connection closed.");
            }

            public void onStatementExecute(AvaticaStatement statement, ResultSink resultSink) {
                logger.debug("statement executed.");
            }

            public void onStatementClose(AvaticaStatement statement) {
                logger.debug("statement closed.");
            }
        };
    }

    @Override
    protected String getConnectStringPrefix() {
        return CONNECT_STRING_PREFIX;
    }

}

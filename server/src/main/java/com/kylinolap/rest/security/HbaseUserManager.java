package com.kylinolap.rest.security;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kylinolap.common.KylinConfig;
import com.kylinolap.common.persistence.HBaseConnection;

public class HbaseUserManager implements UserManager {

    private static final Logger logger = LoggerFactory.getLogger(HbaseUserManager.class);

    private static final String DEFAULT_TABLE_PREFIX = "kylin_metadata";
    private static final String USER_TABLE_NAME = "_user";
    private static final String USER_AUTHORITY_FAMILY = "f";
    private static final String USER_AUTHORITY_COLUMN = "c";
    private String hbaseUrl = null;
    private String tableNameBase = null;
    private String userTableName = null;

    public HbaseUserManager() {
        String metadataUrl = KylinConfig.getInstanceFromEnv().getMetadataUrl();
        // split TABLE@HBASE_URL
        int cut = metadataUrl.indexOf('@');
        tableNameBase = cut < 0 ? DEFAULT_TABLE_PREFIX : metadataUrl.substring(0, cut);
        hbaseUrl = cut < 0 ? metadataUrl : metadataUrl.substring(cut + 1);
        userTableName = tableNameBase + USER_TABLE_NAME;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        HTableInterface htable = null;
        try {
            htable = HBaseConnection.get(hbaseUrl).getTable(userTableName);
            Result result = htable.get(new Get(Bytes.toBytes(username)));
            Collection<? extends GrantedAuthority> authorities = deserialize(result.getValue(Bytes.toBytes(USER_AUTHORITY_FAMILY), Bytes.toBytes(USER_AUTHORITY_COLUMN)));

            return new User(username, "N/A", authorities);
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
        } finally {
            IOUtils.closeQuietly(htable);
        }

        return null;
    }

    @Override
    public void createUser(UserDetails user) {
        updateUser(user);
    }

    @Override
    public void updateUser(UserDetails user) {
        byte[] userAuthorities = serialize(user.getAuthorities());
        HTableInterface htable = null;
        try {
            htable = HBaseConnection.get(hbaseUrl).getTable(userTableName);
            Put put = new Put(Bytes.toBytes(user.getUsername()));
            put.add(Bytes.toBytes(USER_AUTHORITY_FAMILY), Bytes.toBytes(USER_AUTHORITY_COLUMN), userAuthorities);

            htable.put(put);
            htable.flushCommits();
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
        } finally {
            IOUtils.closeQuietly(htable);
        }
    }

    @Override
    public void deleteUser(String username) {
        HTableInterface htable = null;
        try {
            htable = HBaseConnection.get(hbaseUrl).getTable(userTableName);
            Delete delete = new Delete(Bytes.toBytes(username));

            htable.delete(delete);
            htable.flushCommits();
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
        } finally {
            IOUtils.closeQuietly(htable);
        }
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean userExists(String username) {
        HTableInterface htable = null;
        try {
            htable = HBaseConnection.get(hbaseUrl).getTable(userTableName);
            Result result = htable.get(new Get(Bytes.toBytes(username)));

            return null != result && !result.isEmpty();
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
        } finally {
            IOUtils.closeQuietly(htable);
        }

        return false;
    }

    @Override
    public List<String> getUserAuthorities() {
        Scan s = new Scan();
        s.addColumn(Bytes.toBytes(USER_AUTHORITY_FAMILY), Bytes.toBytes(USER_AUTHORITY_COLUMN));

        List<String> authorities = new ArrayList<String>();
        HTableInterface htable = null;
        ResultScanner scanner = null;
        try {
            htable = HBaseConnection.get(hbaseUrl).getTable(userTableName);
            scanner = htable.getScanner(s);

            for (Result result = scanner.next(); result != null; result = scanner.next()) {
                Collection<? extends GrantedAuthority> authCollection = deserialize(result.getValue(Bytes.toBytes(USER_AUTHORITY_FAMILY), Bytes.toBytes(USER_AUTHORITY_COLUMN)));
                
                for (GrantedAuthority auth: authCollection){
                    if (!authorities.contains(auth.getAuthority())){
                        authorities.add(auth.getAuthority());
                    }
                }
            }
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
        } finally {
            IOUtils.closeQuietly(scanner);
            IOUtils.closeQuietly(htable);
        }

        return authorities;
    }

    public static class UserGrantedAuthority implements GrantedAuthority {
        private static final long serialVersionUID = -5128905636841891058L;
        private String authority;

        public UserGrantedAuthority() {
        }

        @Override
        public String getAuthority() {
            return authority;
        }

        public void setAuthority(String authority) {
            this.authority = authority;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((authority == null) ? 0 : authority.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            UserGrantedAuthority other = (UserGrantedAuthority) obj;
            if (authority == null) {
                if (other.authority != null)
                    return false;
            } else if (!authority.equals(other.authority))
                return false;
            return true;
        }
    }

    private Collection<? extends GrantedAuthority> deserialize(byte[] value) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            return Arrays.asList(mapper.readValue(value, UserGrantedAuthority[].class));
        } catch (JsonParseException e) {
            logger.error(e.getLocalizedMessage(), e);
        } catch (JsonMappingException e) {
            logger.error(e.getLocalizedMessage(), e);
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
        }

        return null;
    }

    private byte[] serialize(Collection<? extends GrantedAuthority> collection) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(buf);

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(dout, collection);
            dout.close();
            buf.close();
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
        }

        return buf.toByteArray();
    }

}
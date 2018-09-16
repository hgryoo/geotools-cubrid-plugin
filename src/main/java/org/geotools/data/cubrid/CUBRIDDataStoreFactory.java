/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2018, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.data.cubrid;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataAccessFactory.Param;
import org.geotools.data.Parameter;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.JDBCDataStoreFactory;
import org.geotools.jdbc.SQLDialect;


/**
 * DataStoreFactory for CUBRID database.
 *
 * @author Hyung-Gyu Ryoo, CUBRID
 *
 *
 *
 *
 * @source $URL$
 */
public class CUBRIDDataStoreFactory extends JDBCDataStoreFactory {
    /** parameter for database type */
    public static final Param DBTYPE = new Param("dbtype", String.class, "Type", true, "cubrid",
            Collections.singletonMap(Parameter.LEVEL, "program"));
    /** Default port number for MYSQL */
    public static final Param PORT = new Param("port", Integer.class, "Port", true, 33000);
    
    /** parameter for database schema */
    public static final Param SCHEMA = new Param("schema", String.class, "Schema", false, "public");
    
    public static final Param ALTHOSTS = new Param("althosts", String.class, "AltHosts", false);
    
    protected SQLDialect createSQLDialect(JDBCDataStore dataStore) {
        return new CUBRIDDialectBasic(dataStore);
    }

    public String getDisplayName() {
        return "CUBRID";
    }
    
    protected String getDriverClassName() {
        return "cubrid.jdbc.driver.CUBRIDDriver";
    }

    protected String getDatabaseID() {
        return (String) DBTYPE.sample;
    }

    public String getDescription() {
        return "CUBRID Database";
    }

    @Override
    protected String getValidationQuery() {
        return "select version()";
    }
    
    @Override
    protected void setupParameters(Map parameters) {
        super.setupParameters(parameters);
        parameters.put(DBTYPE.key, DBTYPE);
        parameters.put(SCHEMA.key, SCHEMA);
        parameters.put(PORT.key, PORT);
    }
    
    @Override
    protected JDBCDataStore createDataStoreInternal(JDBCDataStore dataStore, Map params)
            throws IOException {
    	
        return dataStore;
    }

	@Override
	protected String getJDBCUrl(Map params) throws IOException {
        String host = (String) HOST.lookUp(params);
        String db = (String) DATABASE.lookUp(params);
        int port = (Integer) PORT.lookUp(params);
        String schema = (String) SCHEMA.lookUp(params);
        return "jdbc:CUBRID" + ":" + host + ":" + port + ":" + db + ":" + schema + "::";
	}
}

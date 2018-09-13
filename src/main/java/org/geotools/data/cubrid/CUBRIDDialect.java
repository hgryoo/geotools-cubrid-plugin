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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Map;
import java.util.logging.Level;

import org.geotools.factory.Hints;
import org.geotools.geometry.jts.Geometries;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.SQLDialect;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * Delegate for {@link CUBRIDDialectBasic} and {@link CUBRIDDialectPrepared}
 * which implements the common part of the api. 
 * 
 * @author Hyung-Gyu Ryoo, CUBRID
 *
 *
 *
 *
 * @source $URL$
 */
public class CUBRIDDialect extends SQLDialect {

    public CUBRIDDialect(JDBCDataStore dataStore) {
        super(dataStore);
    }
    
    @Override
    public boolean includeTable(String schemaName, String tableName, Connection cx)
            throws SQLException {
        if ("geometry_columns".equalsIgnoreCase(tableName)) {
            return false;
        }
        return super.includeTable(schemaName, tableName, cx);
    }

    public Integer getGeometrySRID(String schemaName, String tableName, String columnName,
        Connection cx) throws SQLException {
        
        //first check the geometry_columns table
        StringBuffer sql = new StringBuffer();
        sql.append("SELECT ");
        encodeColumnName(null, "srid", sql);
        sql.append(" FROM ");
        encodeTableName("geometry_columns", sql);
        sql.append(" WHERE ");
        
        encodeColumnName(null, "f_table_schema", sql);
        
        if (schemaName != null) {
            sql.append( " = '").append(schemaName).append("'");
        }
        else {
            sql.append(" IS NULL");
        }
        sql.append(" AND ");
        
        encodeColumnName(null, "f_table_name", sql);
        sql.append(" = '").append(tableName).append("' AND ");
        
        encodeColumnName(null, "f_geometry_column", sql);
        sql.append(" = '").append(columnName).append("'");
        
        dataStore.getLogger().fine(sql.toString());
        
        Statement st = cx.createStatement();
        try {
            ResultSet rs = st.executeQuery(sql.toString());
            try {
                if (rs.next()) {
                    return new Integer(rs.getInt(1));
                }
            }
            finally {
                dataStore.closeSafe(rs);
            }
        }
        catch(SQLException e) {
            //geometry_columns does not exist
        }
        finally {
            dataStore.closeSafe(st);
        }
        
        //execute SELECT srid(<columnName>) FROM <tableName> LIMIT 1;
        sql = new StringBuffer();
        sql.append("SELECT srid(");
        encodeColumnName(null, columnName, sql);
        sql.append(") ");
        sql.append("FROM ");

        if (schemaName != null) {
            encodeTableName(schemaName, sql);
            sql.append(".");
        }

        encodeSchemaName(tableName, sql);
        sql.append(" WHERE ");
        encodeColumnName(null, columnName, sql);
        sql.append(" is not null LIMIT 1");

        dataStore.getLogger().fine(sql.toString());

        st = cx.createStatement();
        try {
            ResultSet rs = st.executeQuery(sql.toString());

            try {
                if (rs.next()) {
                    return new Integer(rs.getInt(1));
                } else {
                    //could not find out
                    return null;
                }
            } finally {
                dataStore.closeSafe(rs);
            }
        } finally {
            dataStore.closeSafe(st);
        }
    }

    @Override
    public void encodeGeometryColumn(GeometryDescriptor gatt, String prefix,
            int srid, Hints hints, StringBuffer sql) {
        sql.append("asWKB(");
        encodeColumnName(prefix, gatt.getLocalName(), sql);
        sql.append(")");
    }

    public void encodeGeometryEnvelope(String tableName, String geometryColumn, StringBuffer sql) {
        sql.append("asWKB(");
        sql.append("envelope(");
        encodeColumnName(null, geometryColumn, sql);
        sql.append("))");
    }

    @Override
    public void registerSqlTypeToSqlTypeNameOverrides(
            Map<Integer, String> overrides) {
        overrides.put( Types.BOOLEAN, "BOOL");
    }
    
    @Override
    public void encodePostColumnCreateTable(AttributeDescriptor att, StringBuffer sql) {
        //make geometry columns non null in order to be able to index them
        if (att instanceof GeometryDescriptor && !att.isNillable()) {
            sql.append( " NOT NULL");
        }
    }
    
    @Override
    public void postCreateTable(String schemaName, SimpleFeatureType featureType, Connection cx)
            throws SQLException, IOException {
        
        //create teh geometry_columns table if necessary
        DatabaseMetaData md = cx.getMetaData();
        ResultSet rs = md.getTables(null, dataStore.escapeNamePattern(md, schemaName),
                dataStore.escapeNamePattern(md, "geometry_columns"), new String[]{"TABLE"});
        try {
            if (!rs.next()) {
                //create it
                Statement st = cx.createStatement();
                try {
                    StringBuffer sql = new StringBuffer("CREATE TABLE ");
                    encodeTableName("geometry_columns", sql);
                    sql.append("(");
                    encodeColumnName(null, "f_table_schema", sql); sql.append(" varchar(255), ");
                    encodeColumnName(null, "f_table_name", sql); sql.append(" varchar(255), ");
                    encodeColumnName(null, "f_geometry_column", sql); sql.append(" varchar(255), ");
                    encodeColumnName(null, "coord_dimension", sql); sql.append(" int, ");
                    encodeColumnName(null, "srid", sql); sql.append(" int, ");
                    encodeColumnName(null, "type", sql); sql.append(" varchar(32)");
                    sql.append(")");
                    
                    if (LOGGER.isLoggable(Level.FINE)) { LOGGER.fine(sql.toString()); }
                    st.execute(sql.toString());
                }
                finally {
                    dataStore.closeSafe(st);
                }
            }
        }
        finally {
            dataStore.closeSafe(rs);
        }
        
        //create spatial index for all geometry columns
        for (AttributeDescriptor ad : featureType.getAttributeDescriptors()) {
            if (!(ad instanceof GeometryDescriptor)) {
                continue;
            }
            GeometryDescriptor gd = (GeometryDescriptor) ad;
            
            if (!ad.isNillable()) {
                //can only index non null columns
                StringBuffer sql = new StringBuffer("ALTER TABLE ");
                encodeTableName(featureType.getTypeName(), sql);
                sql.append(" ADD SPATIAL INDEX (");
                encodeColumnName(null, gd.getLocalName(), sql);
                sql.append(")");
                
                LOGGER.fine( sql.toString() );
                Statement st = cx.createStatement();
                try {
                    st.execute(sql.toString());
                }
                finally {
                    dataStore.closeSafe(st);
                }
            }
            
            CoordinateReferenceSystem crs = gd.getCoordinateReferenceSystem();
            int srid = -1;
            if (crs != null) {
                Integer i = null;
                try {
                    i = CRS.lookupEpsgCode(crs, true);
                } catch (FactoryException e) {
                    LOGGER.log(Level.FINER, "Could not determine epsg code", e);
                }
                srid = i != null ? i : srid;
            }
            
            StringBuffer sql = new StringBuffer("INSERT INTO ");
            encodeTableName("geometry_columns", sql);
            sql.append(" VALUES (");
            sql.append(schemaName != null ? "'"+schemaName+"'" : "NULL").append(", ");
            sql.append("'").append(featureType.getTypeName()).append("', ");
            sql.append("'").append(ad.getLocalName()).append("', ");
            sql.append("2, ");
            sql.append(srid).append(", ");
            
            
            Geometries g = Geometries.getForBinding((Class<? extends Geometry>) gd.getType().getBinding());
            sql.append("'").append(g != null ? g.getName().toUpperCase() : "GEOMETRY").append("')");
            
            LOGGER.fine( sql.toString() );
            Statement st = cx.createStatement();
            try {
                st.execute(sql.toString());
            }
            finally {
                dataStore.closeSafe(st);
            }
        }
        
    }

    public void encodePrimaryKey(String column, StringBuffer sql) {
        encodeColumnName(null, column, sql);
        sql.append(" int AUTO_INCREMENT PRIMARY KEY");
    }

    @Override
    public boolean lookupGeneratedValuesPostInsert() {
        return true;
    }
    
    @Override
    public Object getLastAutoGeneratedValue(String schemaName, String tableName, String columnName,
            Connection cx) throws SQLException {
        Statement st = cx.createStatement();
        try {
            String sql = "SELECT last_insert_id()";
            dataStore.getLogger().fine( sql);
            
            ResultSet rs = st.executeQuery( sql);
            try {
                if ( rs.next() ) {
                    return rs.getLong(1);
                }
            } 
            finally {
                dataStore.closeSafe(rs);
            }
        }
        finally {
            dataStore.closeSafe(st);
        }

        return null;
    }

    @Override
    public boolean isLimitOffsetSupported() {
        return true;
    }
    
    @Override
    public void applyLimitOffset(StringBuffer sql, int limit, int offset) {
        if(limit >= 0 && limit < Integer.MAX_VALUE) {
            if(offset > 0)
                sql.append(" LIMIT " + offset + ", " + limit);
            else 
                sql.append(" LIMIT " + limit);
        } else if(offset > 0) {
            // MySql pretends to have limit specified along with offset
            sql.append(" LIMIT " + offset + ", " + Long.MAX_VALUE);
        }
    }

    
    @Override
    public void dropIndex(Connection cx, SimpleFeatureType schema, String databaseSchema,
            String indexName) throws SQLException {
        StringBuffer sql = new StringBuffer();
        String escape = getNameEscape();
        sql.append("DROP INDEX ");
        if (databaseSchema != null) {
            encodeSchemaName(databaseSchema, sql);
            sql.append(".");
        }
        // weirdness, index naems are treated as strings...
        sql.append(escape).append(indexName).append(escape);
        sql.append(" on ");
        if (databaseSchema != null) {
            encodeSchemaName(databaseSchema, sql);
            sql.append(".");
        }
        encodeTableName(schema.getTypeName(), sql);

        Statement st = null;
        try {
            st = cx.createStatement();
            st.execute(sql.toString());
            if(!cx.getAutoCommit()) {
                cx.commit();
            }
        } finally {
            dataStore.closeSafe(cx);
        }
    }

	@Override
	public Envelope decodeGeometryEnvelope(ResultSet rs, int column, Connection cx) throws SQLException, IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Geometry decodeGeometryValue(GeometryDescriptor descriptor, ResultSet rs, String column,
			GeometryFactory factory, Connection cx, Hints hints) throws IOException, SQLException {
		// TODO Auto-generated method stub
		return null;
	}
}

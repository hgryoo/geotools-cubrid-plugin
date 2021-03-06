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
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.geotools.data.jdbc.FilterToSQL;
import org.geotools.filter.FilterCapabilities;
import org.locationtech.jts.geom.Geometry;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.spatial.BBOX;
import org.opengis.filter.spatial.Beyond;
import org.opengis.filter.spatial.BinarySpatialOperator;
import org.opengis.filter.spatial.Contains;
import org.opengis.filter.spatial.Crosses;
import org.opengis.filter.spatial.DWithin;
import org.opengis.filter.spatial.Disjoint;
import org.opengis.filter.spatial.DistanceBufferOperator;
import org.opengis.filter.spatial.Equals;
import org.opengis.filter.spatial.Intersects;
import org.opengis.filter.spatial.Overlaps;
import org.opengis.filter.spatial.Touches;
import org.opengis.filter.spatial.Within;

/**
 * @author Hyung-Gyu Ryoo, CUBRID
 *
 * @source $URL$
 */
public class CUBRIDFilterToSQL extends FilterToSQL {

    @Override
    protected FilterCapabilities createFilterCapabilities() {
        //MySQL does not actually implement all of the special functions
        FilterCapabilities caps = super.createFilterCapabilities();
        
        
        /*
         * Spatial filters are not supported yet in CUBRID.
        caps.addType(BBOX.class);
        caps.addType(Contains.class);
        //caps.addType(Crosses.class);
        caps.addType(Disjoint.class);
        caps.addType(Equals.class);
        caps.addType(Intersects.class);
        caps.addType(Overlaps.class);
        caps.addType(Touches.class);
        caps.addType(Within.class);
        caps.addType(Beyond.class);
        */
        
        return caps;
    }
    
    @Override
    protected void visitLiteralGeometry(Literal expression) throws IOException {
        Geometry g = (Geometry) evaluateLiteral(expression, Geometry.class);
    }

    @Override
    protected Object visitBinarySpatialOperator(BinarySpatialOperator filter,
            PropertyName property, Literal geometry, boolean swapped, Object extraData) {
        return visitBinarySpatialOperator(filter, (Expression)property, (Expression)geometry, 
            swapped, extraData);
    }
    
    @Override
    protected Object visitBinarySpatialOperator(BinarySpatialOperator filter, Expression e1,
        Expression e2, Object extraData) {
        return visitBinarySpatialOperator(filter, e1, e2, false, extraData);
    }

    protected Object visitBinarySpatialOperator(BinarySpatialOperator filter, Expression e1,
        Expression e2, boolean swapped, Object extraData) {
    
        try {
            
            if (!(filter instanceof Disjoint)) { 
                out.write("MbrIntersects(");
                e1.accept(this, extraData);
                out.write(",");
                e2.accept(this, extraData);
                out.write(")");
                
                if (!(filter instanceof BBOX)) {
                    out.write(" AND ");
                }
            }
     
            if (filter instanceof BBOX) {
                //nothing to do. already encoded above
                return extraData;
            }
            
            if (filter instanceof DistanceBufferOperator) {
                out.write("Distance(");
                e1.accept(this, extraData);
                out.write(", ");
                e2.accept(this, extraData);
                out.write(")");
                
                if (filter instanceof DWithin) {
                    out.write("<");
                }
                else if (filter instanceof Beyond) {
                    out.write(">");
                }
                else {
                    throw new RuntimeException("Unknown distance operator");
                }
                out.write(Double.toString(((DistanceBufferOperator)filter).getDistance()));
            }
            else if (filter instanceof BBOX) {
              
            }
            else {
             
                if (filter instanceof Contains) {
                    out.write("Contains(");
                }
                else if (filter instanceof Crosses) {
                    out.write("Crosses(");
                }
                else if (filter instanceof Disjoint) {
                    out.write("Disjoint(");
                }
                else if (filter instanceof Equals) {
                    out.write("Equals(");
                }
                else if (filter instanceof Intersects) {
                    out.write("Intersects(");
                }
                else if (filter instanceof Overlaps) {
                    out.write("Overlaps(");
                }
                else if (filter instanceof Touches) {
                    out.write("Touches(");
                }
                else if (filter instanceof Within) {
                    out.write("Within(");
                }
                else {
                    throw new RuntimeException("Unknown operator: " + filter);
                }
                
                if (swapped) {
                    e2.accept(this, extraData);
                    out.write(", ");
                    e1.accept(this, extraData);
                }
                else {
                    e1.accept(this, extraData);
                    out.write(", ");
                    e2.accept(this, extraData);
                }
                
                out.write(")");
            }
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        return extraData;
    }

}

/*******************************************************************************
 *   Gisgraphy Project 
 * 
 *   This library is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Lesser General Public
 *   License as published by the Free Software Foundation; either
 *   version 2.1 of the License, or (at your option) any later version.
 * 
 *   This library is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *   Lesser General Public License for more details.
 * 
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the Free Software
 *   Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA
 * 
 *  Copyright 2008  Gisgraphy project 
 *  David Masclet <davidmasclet@gisgraphy.com>
 *  
 *  
 *******************************************************************************/
package com.gisgraphy.importer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.FlushMode;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import com.gisgraphy.domain.geoloc.entity.City;
import com.gisgraphy.domain.geoloc.entity.GisFeature;
import com.gisgraphy.domain.repository.IGisFeatureDao;
import com.gisgraphy.domain.repository.ISolRSynchroniser;
import com.gisgraphy.domain.valueobject.GISSource;
import com.gisgraphy.domain.valueobject.NameValueDTO;
import com.gisgraphy.fulltext.FullTextSearchEngine;
import com.gisgraphy.helper.GeolocHelper;
import com.vividsolutions.jts.geom.Geometry;

/**
 * Import the quattroshapes localities from an (pre-processed) Quattroshapes data file.
 * The goal of this importer is to cross information between geonames and Quattroshapes 
 * and add shape to cities
 * 
 * 
 * @author <a href="mailto:david.masclet@gisgraphy.com">David Masclet</a>
 */
public class QuattroshapesSimpleImporter extends AbstractSimpleImporterProcessor {
	
	protected static final Logger logger = LoggerFactory.getLogger(QuattroshapesSimpleImporter.class);
	
    protected IGisFeatureDao gisFeatureDao;
    
    

    /* (non-Javadoc)
     * @see com.gisgraphy.domain.geoloc.importer.AbstractImporterProcessor#flushAndClear()
     */
    @Override
    protected void flushAndClear() {
    	gisFeatureDao.flushAndClear();
    }
    

    /* (non-Javadoc)
     * @see com.gisgraphy.domain.geoloc.importer.AbstractImporterProcessor#getFiles()
     */
    @Override
    protected File[] getFiles() {
	return ImporterHelper.listCountryFilesToImport(importerConfig.getQuattroshapesDir());
    }

    /* (non-Javadoc)
     * @see com.gisgraphy.domain.geoloc.importer.AbstractImporterProcessor#getNumberOfColumns()
     */
    @Override
    protected int getNumberOfColumns() {
	return 2;
    }

    /* (non-Javadoc)
     * @see com.gisgraphy.domain.geoloc.importer.AbstractImporterProcessor#processData(java.lang.String)
     */
    @Override
    protected void processData(String line) throws ImporterException {
	String[] fields = line.split("\t");
	String geonamesId=null;
	GisFeature gisFeature;
	
	//
	// Line table has the following fields :
	// --------------------------------------------------- 
	//O : geonames id; 1 shape
	//

	checkNumberOfColumn(fields);
	
	//geonamesId
	if (!isEmptyField(fields, 0, false)) {
		geonamesId=fields[0].trim();
		long geonamesIdAsLong;
		try {
			geonamesIdAsLong = Long.parseLong(geonamesId);
		} catch (NumberFormatException e) {
			logger.error("can not parse geonames id :"+geonamesId);
			return;
		}
		gisFeature = gisFeatureDao.getByFeatureId(geonamesIdAsLong);
		if (gisFeature != null){
			
		} else {
			logger.warn("can not find gisfeature for geonames id "+geonamesId);
			return;
		}
		
	} else {
		logger.warn("There is no geonames Id for "+dumpFields(fields));
		return;
	}
	
	if(!isEmptyField(fields, 1, false)){
		try {
			Geometry shape = (Geometry) GeolocHelper.convertFromHEXEWKBToGeometry(fields[1]);
			gisFeature.setShape(shape);
			if (gisFeature instanceof City){
				gisFeature.setSource(GISSource.GEONAMES_QUATTRO);
				((City) gisFeature).setMunicipality(true);//force to be a municipality.
			}
		    } catch (RuntimeException e) {
		    	logger.warn("can not parse shape for id "+fields[1]+" : "+e);
		    	return;
		    }
	} else { 
		logger.warn("There is no shape for "+dumpFields(fields));
		return;
	}
	
	
	try {
		savecity(gisFeature);
	} catch (ConstraintViolationException e) {
		logger.error("Can not save "+dumpFields(fields)+"(ConstraintViolationException) we continue anyway but you should consider this",e);
	}catch (Exception e) {
		logger.error("Can not save "+dumpFields(fields)+" we continue anyway but you should consider this",e);
	}

    }
    
    /**
     * @param fields
     *                The array to process
     * @return a string which represent a human readable string of the Array but without shape because it is useless in logs
     */
    protected static String dumpFields(String[] fields) {
	String result = "[";
	for (int i=0;i<fields.length;i++) {
		if (i==1){
			result= result+"THE_SHAPE;";
		}else {
	    result = result + fields[i] + ";";
		}
	}
	return result + "]";
    }

	
	void savecity(GisFeature gisFeature) {
		if (gisFeature!=null){
			gisFeatureDao.save(gisFeature);
		}
	}

	

	/* (non-Javadoc)
     * @see com.gisgraphy.domain.geoloc.importer.AbstractImporterProcessor#shouldBeSkiped()
     */
    @Override
    public boolean shouldBeSkipped() {
    	return !importerConfig.isQuattroshapesImporterEnabled() || !importerConfig.isGeonamesImporterEnabled();
    }
    
   


    /* (non-Javadoc)
     * @see com.gisgraphy.domain.geoloc.importer.AbstractImporterProcessor#setCommitFlushMode()
     */
    @Override
    protected void setCommitFlushMode() {
    	this.gisFeatureDao.setFlushMode(FlushMode.COMMIT);
    }

    /* (non-Javadoc)
     * @see com.gisgraphy.domain.geoloc.importer.AbstractImporterProcessor#shouldIgnoreComments()
     */
    @Override
    protected boolean shouldIgnoreComments() {
    	return true;
    }

    /* (non-Javadoc)
     * @see com.gisgraphy.domain.geoloc.importer.AbstractImporterProcessor#shouldIgnoreFirstLine()
     */
    @Override
    protected boolean shouldIgnoreFirstLine() {
    	return false;
    }

    /* (non-Javadoc)
     * @see com.gisgraphy.domain.geoloc.importer.IGeonamesProcessor#rollback()
     */
    public List<NameValueDTO<Integer>> rollback() {
    	List<NameValueDTO<Integer>> deletedObjectInfo = new ArrayList<NameValueDTO<Integer>>();
    	logger.info("reseting quattroshapes");
    	//TODO only cities that have source openstreetmap
    	    deletedObjectInfo
    		    .add(new NameValueDTO<Integer>(City.class.getSimpleName(), 0));
    	resetStatus();
    	return deletedObjectInfo;
    }
    
    
    @Override
    //TODO test
    protected void tearDown() {
    	super.tearDown();
    	String savedMessage = this.statusMessage;
    	try {
    		this.statusMessage = internationalisationService.getString("import.fulltext.optimize");
    	} finally {
    	    // we restore message in case of error
    	    this.statusMessage = savedMessage;
    	}
    }
    


    @Required
	public void setGisFeatureDao(IGisFeatureDao gisFeatureDao) {
		this.gisFeatureDao = gisFeatureDao;
	}
    
    
}
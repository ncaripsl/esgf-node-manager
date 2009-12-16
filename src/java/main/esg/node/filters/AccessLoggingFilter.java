/***************************************************************************
*                                                                          *
*  Organization: Lawrence Livermore National Lab (LLNL)                    *
*   Directorate: Computation                                               *
*    Department: Computing Applications and Research                       *
*      Division: S&T Global Security                                       *
*        Matrix: Atmospheric, Earth and Energy Division                    *
*       Program: PCMDI                                                     *
*       Project: Earth Systems Grid (ESG) Data Node Software Stack         *
*  First Author: Gavin M. Bell (gavin@llnl.gov)                            *
*                                                                          *
****************************************************************************
*                                                                          *
*   Copyright (c) 2009, Lawrence Livermore National Security, LLC.         *
*   Produced at the Lawrence Livermore National Laboratory                 *
*   Written by: Gavin M. Bell (gavin@llnl.gov)                             *
*   LLNL-CODE-420962                                                       *
*                                                                          *
*   All rights reserved. This file is part of the:                         *
*   Earth System Grid (ESG) Data Node Software Stack, Version 1.0          *
*                                                                          *
*   For details, see http://esg-repo.llnl.gov/esg-node/                    *
*   Please also read this link                                             *
*    http://esg-repo.llnl.gov/LICENSE                                      *
*                                                                          *
*   * Redistribution and use in source and binary forms, with or           *
*   without modification, are permitted provided that the following        *
*   conditions are met:                                                    *
*                                                                          *
*   * Redistributions of source code must retain the above copyright       *
*   notice, this list of conditions and the disclaimer below.              *
*                                                                          *
*   * Redistributions in binary form must reproduce the above copyright    *
*   notice, this list of conditions and the disclaimer (as noted below)    *
*   in the documentation and/or other materials provided with the          *
*   distribution.                                                          *
*                                                                          *
*   Neither the name of the LLNS/LLNL nor the names of its contributors    *
*   may be used to endorse or promote products derived from this           *
*   software without specific prior written permission.                    *
*                                                                          *
*   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS    *
*   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT      *
*   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS      *
*   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL LAWRENCE    *
*   LIVERMORE NATIONAL SECURITY, LLC, THE U.S. DEPARTMENT OF ENERGY OR     *
*   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,           *
*   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT       *
*   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF       *
*   USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND    *
*   ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,     *
*   OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT     *
*   OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF     *
*   SUCH DAMAGE.                                                           *
*                                                                          *
***************************************************************************/

/**
   Description:

   The web.xml entry...
   (NOTE: must appear AFTER NCAR's Authorization Filter because they put
    additional information in the request that we need like email address!)

  <!-- Filter for token-based authorization -->
  <filter>
    <filter-name>AccessLoggingFilter</filter-name>
    <filter-class>esg.node.filters.AccessLoggingFilter</filter-class>
    <init-param>
      <param-name>db.driver</param-name>
      <param-value>org.postgresql.Driver</param-value>
      <param-name>db.protocol</param-name>
      <param-value>jdbc:postgresql:</param-value>
      <param-name>db.host</param-name>
      <param-value>localhost</param-value>
      <param-name>db.port</param-name>
      <param-value>5432</param-value>
      <param-name>db.database</param-name>
      <param-value>esg-datanode</param-value>
      <param-name>db.user</param-name>
      <param-value>dbsuper</param-value>
      <param-name>db.password</param-name>
      <param-value>***</param-value>
    </init-param>
  </filter>
  <filter-mapping>
    <filter-name>AccessLoggingFilter</filter-name>
    <url-pattern>/*</url-pattern>
  </filter-mapping>

**/
package esg.node.filters;

import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.*;

import esg.common.db.DatabaseResource;

public class AccessLoggingFilter implements Filter {

    private static Log log = LogFactory.getLog(AccessLoggingFilter.class);
    
    private FilterConfig filterConfig = null;
    private AccessLoggingDAO accessLoggingDAO = null;
    private Properties dbProperties = null;

    public AccessLoggingFilter() {
	log.trace("Instantiating AccessLoggingFilter...");
	dbProperties = new Properties();
	
    }

    public void init(FilterConfig filterConfig) throws ServletException {
	this.filterConfig = filterConfig;
	dbProperties.put("db.protocol",filterConfig.getInitParameter("db.protocol"));
	dbProperties.put("db.host",filterConfig.getInitParameter("db.host"));
	dbProperties.put("db.port",filterConfig.getInitParameter("db.port"));
	dbProperties.put("db.database",filterConfig.getInitParameter("db.database"));
	dbProperties.put("db.user",filterConfig.getInitParameter("db.user"));
	dbProperties.put("db.password",filterConfig.getInitParameter("db.password"));

	log.trace("Database parameters: "+dbProperties);

	DatabaseResource.init(filterConfig.getInitParameter("db.driver")).setupDataSource(dbProperties);
	DatabaseResource.getInstance().showDriverStats();
	accessLoggingDAO = new AccessLoggingDAO(DatabaseResource.getInstance().getDataSource());
	log.trace(accessLoggingDAO.toString());
    }

    public void destroy() { 
	this.filterConfig = null; 
	this.dbProperties.clear();
	this.accessLoggingDAO = null;
	
	//Shutting down this resource under the assuption that no one
	//else is using this resource but us
	DatabaseResource.getInstance().shutdownResource();
    }

    public void doFilter(ServletRequest request,
			 ServletResponse response, 
			 FilterChain chain) throws IOException, ServletException {
	
	if(filterConfig == null) return;
	
	//firewall off any errors so that nothing stops the show...
	try {
	    if(accessLoggingDAO != null) {

		HttpServletRequest req = (HttpServletRequest)request;
		Map<String,String> validationMap = (Map<String,String>)req.getAttribute("validationMap");
		if(validationMap != null) {
		    
		    String userid = validationMap.get("user");
		    String url = req.getRequestURL().toString();
		    String email = validationMap.get("email");
		    
		    accessLoggingDAO.log(userid,url,email);
		}else{
		    log.warn("Validation Map is null ["+validationMap+"]");
		}
	    }else{
		log.warn("DAO is null :["+accessLoggingDAO+"]");
	    }
	}catch(Throwable t) {
	    log.error(t);
	}
	
	chain.doFilter(request, response);
    }
    
}
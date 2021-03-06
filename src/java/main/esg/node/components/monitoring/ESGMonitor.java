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
 *   For details, see http://esgf.org/esg-node/                    *
 *   Please also read this link                                             *
 *    http://esgf.org/LICENSE                                      *
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

   This class is a component implementation that is responsible for
   collecting and disseminating data node information. Statistics
   about the data node operation / usage.

**/
package esg.node.components.monitoring;

import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.*;

import esg.common.db.DatabaseResource;
import esg.common.Utils;
import esg.node.core.*;

public class ESGMonitor extends AbstractDataNodeComponent {
    
    private static Log log = LogFactory.getLog(ESGMonitor.class);
    private Properties props = null;
    private boolean isBusy = false;
    private MonitorDAO monitorDAO = null;

    //Local cache object for results
    private MonitorInfo monitorInfo = null;

    public ESGMonitor(String name) {
        super(name);
        log.debug("Instantiating ESGMonitor...");
    }
    
    public void init() {
        log.info("Initializing ESGMonitor...");
        props = getDataNodeManager().getMatchingProperties("^monitor.*");
        monitorInfo = new MonitorInfo();
        monitorDAO = new MonitorDAO(DatabaseResource.getInstance().getDataSource(),Utils.getNodeID(),props);
        startMonitoring();
    }

    public MonitorInfo getMonitorInfo() { return monitorInfo; }
    
    private synchronized boolean fetchNodeInfo() {
        //log.trace("monitor's fetchNodeInfo() called....");
        boolean ret = true;
        monitorDAO.setMonitorInfo(monitorInfo);
        monitorInfo.componentList = getDataNodeManager().getComponentNames();
        return ret;
    }
    
    private void startMonitoring() {
        log.trace("launching system monitor timer");
        long delay  = Long.parseLong(props.getProperty("monitor.initialDelay"));
        long period = Long.parseLong(props.getProperty("monitor.period"));
        log.trace("monitoring delay: "+delay+" sec");
        log.trace("monitoring period: "+period+" sec");
	
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
                public final void run() {
                    //log.trace("Checking for new node information... [busy? "+ESGMonitor.this.isBusy+"]");
                    if(!ESGMonitor.this.isBusy) {
                        ESGMonitor.this.isBusy = true;
                        if(fetchNodeInfo()) {
                            //TODO
                            monitorDAO.markLastCompletionTime();
                        }
                        ESGMonitor.this.isBusy = false;
                    }
                }
            },delay*1000,period*1000);
    }

    public boolean handleESGQueuedEvent(ESGEvent event) {
        log.trace("handling enqueued event ["+getName()+"]:["+this.getClass().getName()+"]: Got A QueuedEvent!!!!: "+event);
        //TODO: grab the monitor information and push into event

        event.setData(getMonitorInfo().toString());
        enqueueESGEvent(event);
        return true;
    }
    public void handleESGEvent(ESGEvent event) {
        super.handleESGEvent(event);
    }

}

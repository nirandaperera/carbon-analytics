/*
*  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.analytics.spark.event.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.event.stream.core.EventStreamService;

/**
 * Declarative service component for spark eventing.
 *
 * @scr.component name="spark.eventing" immediate="true"
 * @scr.reference name="event.streamService" interface="org.wso2.carbon.event.stream.core.EventStreamService"
 * cardinality="1..1" policy="dynamic" bind="setEventStreamService" unbind="unsetEventStreamService"
 */
public class EventingComponent {

    private static final Log log = LogFactory.getLog(EventingComponent.class);

    protected void activate(ComponentContext ctx) {
        if (log.isDebugEnabled()) {
            log.debug("Activating Spark Eventing");
        }
        try {
            BundleContext bundleContext = ctx.getBundleContext();
            if (log.isDebugEnabled()) {
                log.debug("Finished activating Analytics Spark Eventing");
            }
        } catch (Throwable throwable) {
            log.error("Error in registering the spark eventing service! ", throwable);
        }
    }

    protected void deactivate(ComponentContext ctx) {
        if (log.isDebugEnabled()) {
            log.debug("Deactivating Spark Eventing");
        }
    }

    protected void setEventStreamService(EventStreamService eventStreamService) {
        ServiceHolder.setEventStreamService(eventStreamService);
    }

    protected void unsetEventStreamService(EventStreamService eventStreamService) {
        ServiceHolder.setEventStreamService(null);
    }


}

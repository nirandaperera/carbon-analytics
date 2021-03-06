/**
 * Copyright (c) 2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Date: 7/25/13
 * Time: 4:39 PM
 */

package org.wso2.carbon.analytics.jmx.agent.profiles;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class MBean {

    private String mBeanName;
    private MBeanAttribute[] attributes;

    public String getMBeanName() {
        return mBeanName;
    }

    @XmlElement
    public void setMBeanName(String mBeanName) {
        this.mBeanName = mBeanName;
    }

    public MBeanAttribute[] getAttributes() {
        return attributes;
    }

    @XmlElement
    public void setAttributes(MBeanAttribute[] attributes) {
        this.attributes = attributes;
    }
}

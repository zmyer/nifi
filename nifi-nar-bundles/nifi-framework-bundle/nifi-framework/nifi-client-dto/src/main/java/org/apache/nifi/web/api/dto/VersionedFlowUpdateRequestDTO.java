/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nifi.web.api.dto;

import io.swagger.annotations.ApiModelProperty;
import org.apache.nifi.web.api.dto.util.TimestampAdapter;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Date;

@XmlType(name = "versionedFlowUpdateRequest")
public class VersionedFlowUpdateRequestDTO {
    private String requestId;
    private String processGroupId;
    private String uri;
    private Date lastUpdated;
    private boolean complete = false;
    private String failureReason;
    private int percentCompleted;
    private String state;
    private VersionControlInformationDTO versionControlInformation;

    @ApiModelProperty("The unique ID of the Process Group that the variable registry belongs to")
    public String getProcessGroupId() {
        return processGroupId;
    }

    public void setProcessGroupId(String processGroupId) {
        this.processGroupId = processGroupId;
    }

    @ApiModelProperty(value = "The unique ID of this request.", readOnly = true)
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    @ApiModelProperty(value = "The URI for future requests to this drop request.", readOnly = true)
    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    @XmlJavaTypeAdapter(TimestampAdapter.class)
    @ApiModelProperty(value = "The last time this request was updated.", dataType = "string", readOnly = true)
    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @ApiModelProperty(value = "Whether or not this request has completed", readOnly = true)
    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    @ApiModelProperty(value = "An explanation of why this request failed, or null if this request has not failed", readOnly = true)
    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String reason) {
        this.failureReason = reason;
    }

    @ApiModelProperty(value = "The VersionControlInformation that describes where the Versioned Flow is located; this may not be populated until the request is completed.", readOnly = true)
    public VersionControlInformationDTO getVersionControlInformation() {
        return versionControlInformation;
    }

    public void setVersionControlInformation(VersionControlInformationDTO versionControlInformation) {
        this.versionControlInformation = versionControlInformation;
    }

    @ApiModelProperty(value = "The state of the request", readOnly = true)
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @ApiModelProperty(value = "The percentage complete for the request, between 0 and 100", readOnly = true)
    public int getPercentCompleted() {
        return percentCompleted;
    }

    public void setPercentCompleted(int percentCompleted) {
        this.percentCompleted = percentCompleted;
    }
}

/**
 *  This document is a part of the source code and related artifacts
 *  for CollectionSpace, an open source collections management system
 *  for museums and related institutions:
 *
 *  http://www.collectionspace.org
 *  http://wiki.collectionspace.org
 *
 *  Copyright © 2009 Regents of the University of California
 *
 *  Licensed under the Educational Community License (ECL), Version 2.0.
 *  You may not use this file except in compliance with this License.
 *
 *  You may obtain a copy of the ECL 2.0 License at
 *
 *  https://source.collectionspace.org/collection-space/LICENSE.txt
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.collectionspace.services.propagation;

import org.collectionspace.services.client.PropagationClient;
import org.collectionspace.services.common.ResourceBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * PropagationResource.java
 *
 * Handles requests to the Propagation service, orchestrates the retrieval
 * of relevant resources, and returns responses to the client.
 */
@Path(PropagationClient.SERVICE_PATH)
@Consumes("application/xml")
@Produces("application/xml")
public class PropagationResource extends ResourceBase {

    final Logger logger = LoggerFactory.getLogger(PropagationResource.class);

    @Override
    protected String getVersionString() {
        final String lastChangeRevision = "$LastChangedRevision: 4763 $";
        return lastChangeRevision;
    }

    @Override
    public String getServiceName() {
        return PropagationClient.SERVICE_NAME;
    }

    @Override
    public Class<PropagationsCommon> getCommonPartClass() {
        return PropagationsCommon.class;
    }

}

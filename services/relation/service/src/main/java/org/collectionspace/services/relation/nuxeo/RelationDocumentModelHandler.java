/**
 *  This document is a part of the source code and related artifacts
 *  for CollectionSpace, an open source collections management system
 *  for museums and related institutions:

 *  http://www.collectionspace.org
 *  http://wiki.collectionspace.org

 *  Copyright 2009 University of California at Berkeley

 *  Licensed under the Educational Community License (ECL), Version 2.0.
 *  You may not use this file except in compliance with this License.

 *  You may obtain a copy of the ECL 2.0 License at

 *  https://source.collectionspace.org/collection-space/LICENSE.txt

 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.collectionspace.services.relation.nuxeo;

import java.util.Iterator;

import org.collectionspace.services.client.PoxPayloadIn;
import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.common.ServiceMain;
import org.collectionspace.services.common.api.Tools;
import org.collectionspace.services.common.config.TenantBindingConfigReaderImpl;
import org.collectionspace.services.common.context.ServiceBindingUtils;
import org.collectionspace.services.common.document.InvalidDocumentException;
import org.collectionspace.services.common.relation.RelationJAXBSchema;
import org.collectionspace.services.common.relation.nuxeo.RelationConstants;
import org.collectionspace.services.common.context.ServiceContext;
import org.collectionspace.services.common.repository.RepositoryClient;
import org.collectionspace.services.common.repository.RepositoryClientFactory;
import org.collectionspace.services.common.service.ServiceBindingType;
import org.collectionspace.services.nuxeo.util.NuxeoUtils;
import org.collectionspace.services.relation.RelationsCommon;
import org.collectionspace.services.relation.RelationsCommonList;
import org.collectionspace.services.relation.RelationsCommonList.RelationListItem;

import org.collectionspace.services.common.document.DocumentWrapper;
import org.collectionspace.services.jaxb.AbstractCommonList;
import org.collectionspace.services.nuxeo.client.java.RemoteDocumentModelHandlerImpl;
import org.collectionspace.services.relation.RelationsDocListItem;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.api.repository.RepositoryInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RelationDocumentModelHandler
 *
 * $LastChangedRevision: $
 * $LastChangedDate: $
 */
public class RelationDocumentModelHandler
        extends RemoteDocumentModelHandlerImpl<RelationsCommon, RelationsCommonList> {

    private final Logger logger = LoggerFactory.getLogger(RelationDocumentModelHandler.class);
    /**
     * relation is used to stash JAXB object to use when handle is called
     * for Action.CREATE, Action.UPDATE or Action.GET
     */
    private RelationsCommon relation;
    /**
     * relationList is stashed when handle is called
     * for ACTION.GET_ALL
     */
    private RelationsCommonList relationList;

    @Override
    public void handleCreate(DocumentWrapper<DocumentModel> wrapDoc) throws Exception {

        // Obtain document models for the subject and object of the relation.
        DocumentModel relationDocModel = wrapDoc.getWrappedObject();
        ServiceContext ctx = getServiceContext();
        DocumentModel subjectDocModel = getSubjectDocModel(relationDocModel, ctx);
        DocumentModel objectDocModel = getObjectDocModel(relationDocModel, ctx);

        // Use values from the subject and object document models to populate the
        // relevant fields of the relation's own document model.
        if (subjectDocModel != null) {
            relationDocModel = populateSubjectValues(relationDocModel, subjectDocModel);
        }
        if (objectDocModel != null) {
            relationDocModel = populateObjectValues(relationDocModel, objectDocModel);
        }

        // FIXME: Verify the following:
        // Do we call this method here, only after we've updated the relationDocModel?
        // Has the wrapDoc instance itself been updated, in the process of updating the relationDocModel,
        // or do we need to pass the updated relationDocModel back into that instance?
        super.handleCreate(wrapDoc);

    }

    @Override
    public void handleUpdate(DocumentWrapper<DocumentModel> wrapDoc) throws Exception {
        super.handleUpdate(wrapDoc);
    }

    @Override
    public RelationsCommon getCommonPart() {
        return relation;
    }

    @Override
    public void setCommonPart(RelationsCommon theRelation) {
        this.relation = theRelation;
    }

    /**get associated Relation (for index/GET_ALL)
     */
    @Override
    public RelationsCommonList getCommonPartList() {
        return relationList;
    }

    @Override
    public void setCommonPartList(RelationsCommonList theRelationList) {
        this.relationList = theRelationList;
    }

    @Override
    public RelationsCommon extractCommonPart(DocumentWrapper<DocumentModel> wrapDoc)
            throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void fillCommonPart(RelationsCommon theRelation, DocumentWrapper<DocumentModel> wrapDoc) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public RelationsCommonList extractCommonPartList(DocumentWrapper<DocumentModelList> wrapDoc) throws Exception {
        RelationsCommonList relList = this.extractPagingInfo(new RelationsCommonList(), wrapDoc);
        relList.setFieldsReturned("subjectCsid|relationshipType|predicateDisplayName|objectCsid|uri|csid|subject|object");
        ServiceContext ctx = getServiceContext();
        String serviceContextPath = getServiceContextPath();

        TenantBindingConfigReaderImpl tReader = ServiceMain.getInstance().getTenantBindingConfigReader();
        String serviceName = getServiceContext().getServiceName().toLowerCase();
        ServiceBindingType sbt = tReader.getServiceBinding(ctx.getTenantId(), serviceName);

        Iterator<DocumentModel> iter = wrapDoc.getWrappedObject().iterator();
        while (iter.hasNext()) {
            DocumentModel docModel = iter.next();
            RelationListItem relListItem = getRelationListItem(ctx, sbt, tReader, docModel, serviceContextPath);
            relList.getRelationListItem().add(relListItem);
        }
        return relList;
    }

    /** Gets the relation list item, looking up the subject and object documents, and getting summary
     *  info via the objectName and objectNumber properties in tenant-bindings.
     * @param ctx the ctx
     * @param sbt the ServiceBindingType of Relations service
     * @param tReader the tenant-bindings reader, for looking up docnumber and docname
     * @param docModel the doc model
     * @param serviceContextPath the service context path
     * @return the relation list item, with nested subject and object summary info.
     * @throws Exception the exception
     */
    private RelationListItem getRelationListItem(ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx,
            ServiceBindingType sbt,
            TenantBindingConfigReaderImpl tReader,
            DocumentModel docModel,
            String serviceContextPath) throws Exception {
        RelationListItem relationListItem = new RelationListItem();
        String id = getCsid(docModel);
        relationListItem.setCsid(id);

        relationListItem.setSubjectCsid((String) docModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.DOCUMENT_ID_1));

        String predicate = (String) docModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.RELATIONSHIP_TYPE);
        relationListItem.setRelationshipType(predicate);
        relationListItem.setPredicate(predicate); //predicate is new name for relationshipType.
        relationListItem.setPredicateDisplayName((String) docModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.RELATIONSHIP_TYPE_DISPLAYNAME));

        relationListItem.setObjectCsid((String) docModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.DOCUMENT_ID_2));

        relationListItem.setUri(serviceContextPath + id);

        //Now fill in summary info for the related docs: subject and object.
        String subjectCsid = relationListItem.getSubjectCsid();
        String documentType = (String) docModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.DOCUMENT_TYPE_1);
        RelationsDocListItem subject = createRelationsDocListItem(ctx, sbt, subjectCsid, tReader, documentType);

        //Object o1 =  docModel.getProperty(ctx.getCommonPartLabel(), "subject");
        //Object o2 =  docModel.getProperty(ctx.getCommonPartLabel(), "object");

        String subjectUri = (String) docModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.SUBJECT_URI);
        subject.setUri(subjectUri);
        relationListItem.setSubject(subject);

        String objectCsid = relationListItem.getObjectCsid();
        String documentType2 = (String) docModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.DOCUMENT_TYPE_2);
        RelationsDocListItem object = createRelationsDocListItem(ctx, sbt, objectCsid, tReader, documentType2);

        String objectUri = (String) docModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.OBJECT_URI);
        object.setUri(objectUri);
        relationListItem.setObject(object);

        return relationListItem;
    }

    // DocumentModel itemDocModel = docModelFromCSID(ctx, itemCsid);
    protected RelationsDocListItem createRelationsDocListItem(ServiceContext ctx,
            ServiceBindingType sbt,
            String itemCsid,
            TenantBindingConfigReaderImpl tReader,
            String documentType) throws Exception {
        RelationsDocListItem item = new RelationsDocListItem();
        item.setDocumentType(documentType);//this one comes from the record, as documentType1, documentType2.
        // CSPACE-4037 REMOVING: item.setService(documentType);//this one comes from the record, as documentType1, documentType2.   Current app seems to use servicename for this.
        item.setCsid(itemCsid);

        DocumentModel itemDocModel = NuxeoUtils.getDocFromCsid(getRepositorySession(), ctx, itemCsid);    //null if not found.
        if (itemDocModel != null) {
            String itemDocType = itemDocModel.getDocumentType().getName();
            // CSPACE-4037 REMOVING: item.setDocumentTypeFromModel(itemDocType);           //this one comes from the nuxeo documentType

            //DEBUG: System.out.println("\r\n******** AuthorityItemDocumentModelHandlder documentType **************\r\n\tdocModel: "+itemDocType+"\r\n\tpayload: "+documentType);
            //boolean usedDocumentTypeFromPayload = true;
            /*if ( ! Tools.isBlank(documentType)){
            if (documentType.equals(itemDocType)){
            //usedDocumentTypeFromPayload = true;
            }  else {
            // Laramie20110510 CSPACE-3739  throw the exception for 3739, otherwise, don't throw it.
            //throw new Exception("documentType supplied was wrong.  supplied: "+documentType+" required: "+itemDocType+ " itemCsid: "+itemCsid );
            }
            } else {
            //usedDocumentTypeFromPayload = false;
            item.setDocumentType(itemDocType);
            }   */
            if (Tools.isBlank(documentType)) {
                item.setDocumentType(itemDocType);
            }

            // TODO: clean all the output statements out of here when CSPACE-4037 is done.
            //TODO: ensure that itemDocType is really the entry point, i.e. servicename==doctype
            //ServiceBindingType itemSbt2 = tReader.getServiceBinding(ctx.getTenantId(), itemDocType);
            String propName = "ERROR-FINDING-PROP-VALUE";
            ServiceBindingType itemSbt = tReader.getServiceBindingForDocType(ctx.getTenantId(), itemDocType);
            try {
                propName = ServiceBindingUtils.getPropertyValue(itemSbt, ServiceBindingUtils.OBJ_NAME_PROP);
                String itemDocname = ServiceBindingUtils.getMappedFieldInDoc(itemSbt, ServiceBindingUtils.OBJ_NAME_PROP, itemDocModel);
                if (propName == null || itemDocname == null) {
                    //System.out.println("=== prop NOT found: "+ServiceBindingUtils.OBJ_NAME_PROP+"::"+propName+"="+itemDocname+" documentType: "+documentType);
                } else {
                    item.setName(itemDocname);
                    //System.out.println("=== found prop : "+ServiceBindingUtils.OBJ_NAME_PROP+"::"+propName+"="+itemDocname+" documentType: "+documentType);
                }
            } catch (Throwable t) {
                System.out.println("====Error finding objectNameProperty: " + itemDocModel + " field " + ServiceBindingUtils.OBJ_NAME_PROP + "=" + propName
                        + " not found in itemDocType: " + itemDocType + " inner: " + t.getMessage());
            }
            propName = "ERROR-FINDING-PROP-VALUE";
            try {
                propName = ServiceBindingUtils.getPropertyValue(itemSbt, ServiceBindingUtils.OBJ_NUMBER_PROP);
                String itemDocnumber = ServiceBindingUtils.getMappedFieldInDoc(itemSbt, ServiceBindingUtils.OBJ_NUMBER_PROP, itemDocModel);

                if (propName == null || itemDocnumber == null) {
                    //System.out.println("=== prop NOT found: "+ServiceBindingUtils.OBJ_NUMBER_PROP+"::"+propName+"="+itemDocnumber
                    //                          +" documentType: "+documentType);
                } else {
                    item.setNumber(itemDocnumber);
                    //System.out.println("============ found prop : "+ServiceBindingUtils.OBJ_NUMBER_PROP+"::"+propName+"="+itemDocnumber
                    //                          +" documentType: "+documentType);
                }
            } catch (Throwable t) {
                logger.error("====Error finding objectNumberProperty: " + ServiceBindingUtils.OBJ_NUMBER_PROP + "=" + propName
                        + " not found in itemDocType: " + itemDocType + " inner: " + t.getMessage());
            }
        } else {
            item.setError("INVALID: related object is absent");
            // Laramie20110510 CSPACE-3739  throw the exception for 3739, otherwise, don't throw it.
            //throw new Exception("INVALID: related object is absent "+itemCsid);
        }
        return item;
    }

    @Override
    public String getQProperty(String prop) {
        return "/" + RelationConstants.NUXEO_SCHEMA_ROOT_ELEMENT + "/" + prop;
    }

    /**
     * Obtains the subject resource and uses its values to populate
     * subject-related fields in the relation resource.
     */
    private DocumentModel getSubjectDocModel(DocumentModel relationDocModel, ServiceContext ctx) throws Exception {
        // Get the document model for the subject of the relation.
        DocumentModel subjectDocModel = null;
        String subjectCsid = "";
        String subjectRefName = "";
        // FIXME: Currently assumes that the object CSID is valid if present
        // in the incoming payload.
        try {
            subjectCsid = (String) relationDocModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.SUBJECT_CSID);
            // FIXME: Remove this entire 'if' statement when legacy fields are removed from the Relation record:
            if (Tools.isBlank(subjectCsid)) {
                subjectCsid = (String) relationDocModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.DOCUMENT_ID_1);
            }
        } catch (PropertyException pe) {
            // Per CSPACE-4468, ignore any property exception here.
            // (See parallel comment below in getObjectDocModel.)
        }
        if (Tools.notBlank(subjectCsid)) {
            // FIXME: Call a utility routine here that uses the CSID to retrieve the subject record's docModel.
            // The following is a placeholder:
            subjectDocModel = getDocModelFromCsid(subjectCsid);
        }
        if (Tools.isBlank(subjectCsid)) {
            try {
                subjectRefName = (String) relationDocModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.SUBJECT_REFNAME);
                // FIXME: Call a utility routine here - for which the method below is currently a
                // placeholder - that uses the refName to retrieve the subject record's docModel.
                subjectDocModel = getDocModelFromRefname(subjectRefName);
            } catch (Exception e) {
                throw new InvalidDocumentException(
                        "Relation record must contain a CSID or refName to identify the subject of the relation.", e);
            }
        }
        return subjectDocModel;
    }

    /**
     * Obtains the object resource and uses its values to populate
     * object-related fields in the relation resource.
     */
    private DocumentModel getObjectDocModel(DocumentModel relationDocModel, ServiceContext ctx) throws Exception {
        // Get the document model for the object of the relation.
        String objectCsid = "";
        String objectRefName = "";
        DocumentModel objectDocModel = null;
        // FIXME: Currently assumes that the object CSID is valid if present
        // in the incoming payload.
        try {
            objectCsid = (String) relationDocModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.OBJECT_CSID);
            // FIXME: Remove this entire 'if' statement when legacy fields are removed from the Relation record:
            if (Tools.isBlank(objectCsid)) {
                objectCsid = (String) relationDocModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.DOCUMENT_ID_2);
            }
        } catch (PropertyException pe) {
            // Per CSPACE-4468, ignore any property exception here.
            // The objectCsid and/or subjectCsid field in a relation record
            // can now be null (missing), because a refName value can be
            // provided as an alternate identifier.
        }
        if (Tools.isBlank(objectCsid)) {
            try {
                objectRefName = (String) relationDocModel.getProperty(ctx.getCommonPartLabel(), RelationJAXBSchema.OBJECT_REFNAME);
                // FIXME: Call a utility routine here - for which the method below is currently a
                // placeholder - that uses the refName to retrieve the object record's docModel.
                objectDocModel = getDocModelFromRefname(objectRefName);
            } catch (Exception e) {
                throw new InvalidDocumentException(
                        "Relation record must have a CSID or refName to identify the object of the relation.", e);
            }
        }
        return objectDocModel;
    }

    // CSPACE-4468 placeholder methods:
    
    // FIXME: Placeholder method.
    private DocumentModel getDocModelFromCsid(String csid) {
        return null;
    }

    // FIXME: Placeholder method.
    // Patrick is providing a working replacement for this method, in a framework class.
    private DocumentModel getDocModelFromRefname(String csid) {
        return null;
    }

    // FIXME: Placeholder method.
    private DocumentModel populateSubjectValues(DocumentModel relationDocModel, DocumentModel objectDocModel) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // FIXME: Placeholder method.
    private DocumentModel populateObjectValues(DocumentModel relationDocModel, DocumentModel objectDocModel) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}

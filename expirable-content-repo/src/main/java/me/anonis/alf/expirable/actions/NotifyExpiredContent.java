package me.anonis.alf.expirable.actions;

import java.io.IOException;
import java.io.Serializable;

import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import org.springframework.mail.javamail.JavaMailSender;

import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;

import javax.activation.DataHandler;

import javax.mail.util.ByteArrayDataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import me.anonis.alf.expirable.beans.ReportData;
import me.anonis.alf.expirable.beans.ReportWriter;
import me.anonis.alf.expirable.model.ExpirableContentModel;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.repo.content.transform.ContentTransformer;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.PersonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NotifyExpiredContent extends ActionExecuterAbstractBase {
    private static final Logger LOG = LoggerFactory.getLogger(NotifyExpiredContent.class);

    public static final String NAME = "notify-expired-content";

    public static final String PARAM_FROM = "from";
    public static final String PARAM_TO = "to";
    public static final String PARAM_SUBJECT = "subject";
    public static final String PARAM_BODY = "body";
    public static final String PARAM_BCC = "bcc";
    public static final String PARAM_CONVERT = "convert";
    public static final String PARAM_ATTACHMENT = "attachments";

    private ContentService contentService;
    private ServiceRegistry serviceRegistry;
    private SearchService searchService;
    private NodeService nodeService;
    private PersonService personService = serviceRegistry.getPersonService();
    private ReportWriter reportWriter;

    protected JavaMailSender mailService;

    // this function is copied from: https://raw.githubusercontent.com/mxc/alfresco-emaildocuments-repo/2773523ea40f4a6769251e31239cb03f7490d15c/src/main/java/za/co/jumpingbean/alfresco/repo/EmailDocumentsAction.java:149
    public void addAttachement(final NodeRef nodeRef, MimeMultipart content, final Boolean convert) throws MessagingException {

        MappedByteBuffer buf;
        byte[] array = new byte[0];
        ContentReader reader = serviceRegistry.getContentService().getReader(nodeRef, ContentModel.PROP_CONTENT);
        String fileName = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
        String type = reader.getMimetype().split("/")[0];
        if (!type.equalsIgnoreCase("image")
                && !reader.getMimetype().equalsIgnoreCase(MimetypeMap.MIMETYPE_PDF)
                && convert) {
            ContentWriter writer = contentService.getTempWriter();
            writer.setMimetype(MimetypeMap.MIMETYPE_PDF);
            String srcMimeType = reader.getMimetype();
            ContentTransformer transformer = contentService.getTransformer(srcMimeType, MimetypeMap.MIMETYPE_PDF);
            if (transformer != null) {
                try {
                    transformer.transform(reader, writer);
                    reader = writer.getReader();
                    fileName = fileName.substring(0, fileName.lastIndexOf('.'));
                    fileName += ".pdf";
                } catch (ContentIOException ex) {
                    LOG.warn("could not transform content");
                    //LOG.warn(ex);
                    reader = serviceRegistry.getContentService().getReader(nodeRef, ContentModel.PROP_CONTENT);
                }
            }
        }
        try {
            buf = reader.getFileChannel().map(FileChannel.MapMode.READ_ONLY, 0, reader.getSize());
            if (reader.getSize() <= Integer.MAX_VALUE) {
                array = new byte[(int) reader.getSize()];
                buf.get(array);
            }
        } catch (IOException ex) {
            LOG.error("There was an error reading file into memory.");
            //LOG.error(ex);
            array = new byte[0];
        }

        ByteArrayDataSource ds = new ByteArrayDataSource(array, reader.getMimetype());
        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setFileName(fileName);
        attachment.setDataHandler(
                new DataHandler(ds));
        content.addBodyPart(attachment);
    }   

    @Override
    public void executeImpl(Action ruleAction, NodeRef actionedUponNodeRef) {
        List<ReportData> reportDataList = new ArrayList<ReportData>();
        ResultSet expiredDocs = getExpiredContent();
        for (int i = 0; i < expiredDocs.length(); i++) {
            NodeRef expiredDoc = expiredDocs.getNodeRef(i);

            if (nodeService.exists(expiredDoc)) {
                ReportData reportData = new ReportData();
                reportData.setName((String) nodeService.getProperty(expiredDoc, ContentModel.PROP_NAME));
                String nodeRefStr = expiredDoc.toString();
                reportData.setNodeRef(nodeRefStr);
                reportData.setExpirationDate((Date) nodeService.getProperty(expiredDoc, ExpirableContentModel.PROP_EXPIRATION_DATE));
                reportData.setPath(nodeService.getPath(expiredDoc).toString());

                String ownerEmail;

                try {
                    String ownerUsername = (String) nodeService.getProperty(expiredDoc, ContentModel.PROP_OWNER);
                    if (ownerUsername == null) {
                        LOG.warn("Cannot get owner (ownerUsername == null) of "+nodeRefStr);
                        continue;
                    }
                    NodeRef owner = personService.getPersonOrNull(ownerUsername);
                    if (owner == null) {
                        LOG.warn("Cannot get owner (owner == null) of "+nodeRefStr);
                        continue;
                    }
                    ownerEmail = (String) nodeService.getProperty(owner, ContentModel.PROP_EMAIL);
                } catch(InvalidNodeRefException ex) {
                    LOG.warn("Cannot get email of owner of "+nodeRefStr);
                    continue;
                }

                try {
                    //nodeService.notifyNode(expiredDoc);
                    MimeMessage mimeMessage = mailService.createMimeMessage();
                    mimeMessage.setFrom(new InternetAddress((String) ruleAction.getParameterValue(PARAM_FROM)));
                    mimeMessage.setRecipients(Message.RecipientType.TO, ownerEmail);
                    mimeMessage.setSubject((String) nodeService.getProperty(expiredDoc, ContentModel.PROP_NAME));

                    MimeMultipart mail = new MimeMultipart("mixed");
                    MimeBodyPart bodyText = new MimeBodyPart();
                    bodyText.setText("test1");
                    addAttachement(expiredDoc, mail, false);
                    mimeMessage.setContent(mail);
                } catch (InvalidNodeRefException inre) {
                    LOG.warn("Tried to notify an invalid node, skipping: " + nodeRefStr);
                    continue;
                } catch (javax.mail.internet.AddressException ex) {
                    LOG.warn("javax.mail.internet.AddressException, " + nodeRefStr);
                    continue;
                } catch (javax.mail.MessagingException ex) {
                    LOG.warn("javax.mail.MessagingException, " + nodeRefStr);
                    continue;
                }

                reportDataList.add(reportData);
            }
        }
        if (reportDataList.size() > 0) {
            LOG.info(reportDataList.size() + " expired documents.");
            reportWriter.save(reportDataList);
        } else {
            LOG.info("Nothing expired.");
        }
    }

    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> paramList) {
        paramList.add(new ParameterDefinitionImpl(PARAM_FROM, DataTypeDefinition.TEXT, true, PARAM_FROM));
        paramList.add(new ParameterDefinitionImpl(PARAM_CONVERT, DataTypeDefinition.BOOLEAN, true, PARAM_CONVERT));
    }

    public ResultSet getExpiredContent() {
        String query = "ASPECT:\"cxme:expirable\" AND cxme:expirationDate:[MIN to \"" + Instant.now().toString() + "\"]";
        return searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_FTS_ALFRESCO, query);
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    public void setReportWriter(ReportWriter reportWriter) {
        this.reportWriter = reportWriter;
    }
}

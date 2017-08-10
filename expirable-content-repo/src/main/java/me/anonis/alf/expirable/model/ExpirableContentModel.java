package me.anonis.alf.expirable.model;

import org.alfresco.service.namespace.QName;

public class ExpirableContentModel {
    public static final String XAIONARO_NAMESPACE = "https://alf.anonis.me/model/expirable/1.0";
    public static final QName ASPECT_EXPIRABLE = QName.createQName(XAIONARO_NAMESPACE, "expirable");
    public static final QName PROP_EXPIRATION_DATE = QName.createQName(XAIONARO_NAMESPACE, "expirationDate");
    public static final QName PROP_LAST_NOTIFICATION_DATE = QName.createQName(XAIONARO_NAMESPACE, "lastNotificationDate");
}

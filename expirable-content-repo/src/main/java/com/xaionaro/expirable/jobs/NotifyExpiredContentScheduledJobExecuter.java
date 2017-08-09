package me.anonis.alf.expirable.jobs;

import java.io.Serializable;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.Date;
import java.util.HashMap;
//import java.util.HashSet;
//import java.util.LinkedHashSet;
//import java.util.List;
import java.util.Map;
//import java.util.Set;
//import java.util.StringTokenizer;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ActionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.anonis.alf.expirable.actions.NotifyExpiredContent;

public class NotifyExpiredContentScheduledJobExecuter {
    private static final Logger LOG = LoggerFactory.getLogger(NotifyExpiredContentScheduledJobExecuter.class);

    /**
     * Public API access
     */
    private ServiceRegistry serviceRegistry;

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    /**
     * Executer implementation
     */
    public void execute() {
        LOG.info("Running the scheduled job: Notify Expired Content");

        ActionService actionService = serviceRegistry.getActionService();

        Map<String, Serializable> params = new HashMap<>();
        params.put(NotifyExpiredContent.PARAM_FROM, "notifier@alf.anonis.me");
        params.put(NotifyExpiredContent.PARAM_CONVERT, false);

        Action action = actionService.createAction("notify-expired-content", params);

        actionService.executeAction(action, null);
    }
}

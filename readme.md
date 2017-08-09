# Alfresco Expirable Content

It's a very ugly-hacky fork of [https://github.com/Conexiam/alfresco-expirable-content](https://github.com/Conexiam/alfresco-expirable-content), but notifing about expired documents (instead of deleting them)

This add-on gives you the ability to add an expiration date to content. When the expiration date is in the past, the content is notified about.

The expiration date is displayed but is not editable in the Share UI. That's because
this property is intended to be set by a workflow, folder rule, script or some other
API. If you let anyone edit that property, someone who ordinarily might not be able
to notify about a document could notify something simply by adjusting the expiration date.

The add-on comes with a scheduled job that will run periodically to notify expired
content. By default, the job runs every 5 minutes. To change that, set the following
in alfresco-global.properties:

    ```
    me.anonis.alf.expirable.jobs.notifyExpiredContent.cronexpression=0 0/5 * * * ?
    me.anonis.alf.expirable.jobs.notifyExpiredContent.cronstartdelay=240000
    ```

Any time the action runs it will write a report that lists what was notified about. The
report can be found in Data Dictionary/Expirable Content Reports. That folder will
be created automatically when the server starts up for the first time after the
module is installed.

## Manual Installation

Building this project creates two AMPs. One for the repo tier and one for the Share tier.

Copy the AMPs to the server into their respective "amps" and "amps_share" directories,
then use the MMT to install them. For many installations this means switching to
Alfresco Home then running `bin/apply_amps.sh`.

Undertow
========
Undertow is a Java web server based on non-blocking IO. It consists of a few different parts:

- A core HTTP server that supports both blocking and non-blocking IO
- A Servlet 4.0/5.0/6.0 implementation
- A JSR-356/Jakarta 2.0 compliant Web Socket implementation

Website: http://undertow.io

Issues: https://issues.redhat.com/projects/UNDERTOW

Project Lead: Flavia Rainone <frainone@redhat.com>

Undertow Dev Group: https://groups.google.com/g/undertow-dev/
(you can access archived discussions of old undertow-dev mail list [here](http://lists.jboss.org/mailman/listinfo/undertow-dev))

Zulip Chat: https://wildfly.zulipchat.com stream [#undertow](https://wildfly.zulipchat.com/#narrow/stream/174183-undertow)

Code Repositories
-----------------

Undertow upstream can be found in GitHub, at the address https://github.com/undertow-io/undertow.

Our internal repository for embargoed fixes and SP releases is in GitLab, at https://gitlab.cee.redhat.com/undertow-io/undertow.

SP Releases
-----------
When there are embargoed fixes to be included in the next Undertow release, an SP branch is created for that release after the upstream release is published in GitHub.

For a particular release called X.Y.Z.Final in upstream, the corresponding branch in GitLab is called X.Y.Z.SP. That branch contains the upstream tag, plus the embargoed fixes, followed by the SP tag for that particular release. The tag names follow the format X.Y.ZPn, where n is 1, 2, and so on. Usually only SP1 tag is needed, but we follow the SP2, SP3, etc sequence if more than an SP tag is necessary for release X.Y.Z.Final.

Internal Branch for Embargoed Fixes
-----------------------------------
Whenever you need to submit a fix for an embargoed bug, please submit the merge request to [embargoed-main](https://gitlab.cee.redhat.com/undertow-io/undertow/-/tree/embargoed-main) branch.
Merged PRs to that branch will be routinely added by the project maintainer to the appropriate SP branches until the embargo is lifted. There is no need for any other action by the fix submitter other than making sure your merge request was reviewed and merged.

If your fix relies on an old version of a class in Undertow, say, a class whose code has changed dramatically in main, then you might need to submit the merge request to
[embargoed-2.2.x](https://gitlab.cee.redhat.com/undertow-io/undertow/-/tree/embargoed-2.2.x) or [embargoed-2.3.x](https://gitlab.cee.redhat.com/undertow-io/undertow/-/tree/embargoed-2.3.x) instead.
It is also possible to submit multiple versions of your fix in that case, one for each embargoed branch. Do so only if the fixes differ. Otherwise, a single merge request is preferrable.

Finding The List of Embargoed Fixes Contained in the Next Release
-----------------------------------------------------------------

As a convention to indicate if a fix will be included in the next SP release, whenever an embargoed fix is merged to embargoed-main or one of embargoed-2.2.x and embargoed-2.3.x branches, it is considered ready for release, and it will be continually added to the next SP branches until the embargo is lifted.
Because SP releases are always added to Jira, you can keep track of SP roadmaps there as well.
Last, you can check the status of CVEs in the [CVEs and SP Release Tracking spreadsheet](https://docs.google.com/spreadsheets/d/1HdOGQeNysCGgO7DjzGSZK_OjJoRfHfv9wmzWxjIWimU/edit?usp=sharing).

Notifying and Discussing Security Relevant Bugs
-----------------------------------------------

You can use one of the following internal channels to get in touch with us and notify security relevant bugs internally:
* email frainone@redhat.com, with a copy to secalert@redhat.com
* start a discussion at the prod sec Slack thread, [#help-prodsec-appsvcs-mw](https://redhat.enterprise.slack.com/archives/C06VDTE1CGY), and mention Flavia in your message so she can be notified
* start a discussion at the Slack thread for the JBoss EAP IO & Core Projects team, [#team-jboss-eap-io-core](https://redhat.enterprise.slack.com/archives/C06K06FENAC). This can be particularly useful if you are not entirely sure about the bug you found and you need to discuss it with the team before defining if it is security relevant.
* mention [@Flavia Rainone](https://issues.redhat.com/secure/ViewProfile.jspa) in the Jira related to the bug. Make sure that the comment is not public. Finally, if the Jira is under embargo, verify if Flavia is added to the list of involved users (Involved field at the Jira ticket), or else she will not be able to view it.

In case Flavia is on PTO (check [JBoss IO Team](https://calendar.google.com/calendar/embed?src=c_e97jei7ifd6kifr5r2ao8lqu9c%40group.calendar.google.com) or [JBoss EAP Dev](https://calendar.google.com/calendar/embed?src=redhat.com_auc425v2gk0r1cktvegdi33k98%40group.calendar.google.com) calendar), please contact Richard Opalka,
co-lead of the project, using the same channels listed above.
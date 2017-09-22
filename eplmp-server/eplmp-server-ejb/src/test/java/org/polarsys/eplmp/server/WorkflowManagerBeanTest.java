/*******************************************************************************
  * Copyright (c) 2017 DocDoku.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    DocDoku - initial API and implementation
  *******************************************************************************/

package org.polarsys.eplmp.server;

import org.polarsys.eplmp.core.common.*;
import org.polarsys.eplmp.core.exceptions.AccessRightException;
import org.polarsys.eplmp.core.security.ACL;
import org.polarsys.eplmp.core.security.ACLPermission;
import org.polarsys.eplmp.core.services.IUserManagerLocal;
import org.polarsys.eplmp.core.workflow.WorkflowModel;
import org.polarsys.eplmp.core.workflow.WorkflowModelKey;
import org.polarsys.eplmp.server.util.WorkflowUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;

import javax.persistence.EntityManager;
import javax.persistence.StoredProcedureQuery;
import javax.persistence.TypedQuery;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.MockitoAnnotations.initMocks;

public class WorkflowManagerBeanTest {


    @InjectMocks
    WorkflowManagerBean workflowManagerBean = new WorkflowManagerBean();

    @Mock
    private EntityManager em;
    @Mock
    private IUserManagerLocal userManager;
    @Mock
    TypedQuery<ACL> aclTypedQuery;
    @Mock
    StoredProcedureQuery storedProcedureQuery;

    private User user;
    private Account account;
    private Workspace workspace;

    @Before
    public void setup() throws Exception {
        initMocks(this);
        account = new Account(WorkflowUtil.ADMIN_LOGIN, WorkflowUtil.ADMIN_NAME, WorkflowUtil.ADMIN_MAIL, "en", new Date(), null);
        workspace = new Workspace(WorkflowUtil.WORKSPACE_ID, account, WorkflowUtil.WORKSPACE_DESCRIPTION, false);
        user = new User(workspace,new Account(WorkflowUtil.USER_LOGIN, WorkflowUtil.USER_NAME,WorkflowUtil.USER_MAIL, "en", new Date(), null));
    }

    /**
     * test the remove of acl from a workflow operated by user who doesn't have write access to the workflow
     * @throws Exception
     */
    @Test(expected = AccessRightException.class)
    public void testRemoveACLFromWorkflow() throws Exception {

        //Given
        WorkflowModel workflowModel = new WorkflowModel(workspace, WorkflowUtil.WORKSPACE_ID, user, "");
        ACL acl = new ACL();
        acl.addEntry(user, ACLPermission.READ_ONLY);
        workflowModel.setAcl(acl);
        // User has read access to the workspace
        Mockito.when(userManager.checkWorkspaceReadAccess(WorkflowUtil.WORKSPACE_ID)).thenReturn(user);
        Mockito.when(em.find(WorkflowModel.class, new WorkflowModelKey(WorkflowUtil.WORKSPACE_ID,WorkflowUtil.WORKFLOW_MODEL_ID))).thenReturn(workflowModel);
        //When
        workflowManagerBean.removeACLFromWorkflow(WorkflowUtil.WORKSPACE_ID, WorkflowUtil.WORKFLOW_MODEL_ID);
        //Then, removeACLFromWorkflow should throw AccessRightException, user doesn't have write access to the workflow
    }

    /**
     * create an ACL of the workflow, user is the admin of the workspace
     * @throws Exception
     */
    @Test
    public void testUpdateACLForWorkflowWithNoACL() throws Exception {

        //Given
        WorkflowModel workflowModel = new WorkflowModel(workspace, WorkflowUtil.WORKSPACE_ID, user, "");
        Map<String, String> userEntries = new HashMap<>();
        User user2 = new User(workspace,new Account(WorkflowUtil.USER2_LOGIN , WorkflowUtil.USER2_NAME,WorkflowUtil.USER2_MAIL, "en", new Date(), null));
        User user3 = new User(workspace,new Account(WorkflowUtil.USER3_LOGIN , WorkflowUtil.USER3_NAME,WorkflowUtil.USER3_MAIL, "en", new Date(), null));
        userEntries.put(user.getLogin(), ACLPermission.FORBIDDEN.name());
        userEntries.put(user2.getLogin(), ACLPermission.READ_ONLY.name());
        userEntries.put(user3.getLogin(), ACLPermission.FULL_ACCESS.name());

        // User has read access to the workspace
        Mockito.when(userManager.checkWorkspaceReadAccess(WorkflowUtil.WORKSPACE_ID)).thenReturn(user);
        Mockito.when(em.find(WorkflowModel.class, new WorkflowModelKey(WorkflowUtil.WORKSPACE_ID, WorkflowUtil.WORKFLOW_MODEL_ID))).thenReturn(workflowModel);
        Mockito.when(em.find(User.class, new UserKey(WorkflowUtil.WORKSPACE_ID, WorkflowUtil.USER_LOGIN))).thenReturn(user);
        Mockito.when(em.find(User.class, new UserKey(WorkflowUtil.WORKSPACE_ID,WorkflowUtil.USER2_LOGIN))).thenReturn(user2);
        Mockito.when(em.find(User.class, new UserKey(WorkflowUtil.WORKSPACE_ID,WorkflowUtil.USER3_LOGIN))).thenReturn(user3);

        //When
        WorkflowModel workflow= workflowManagerBean.updateACLForWorkflow(WorkflowUtil.WORKSPACE_ID, WorkflowUtil.WORKFLOW_MODEL_ID, userEntries, null);
        //Then
        Assert.assertEquals(workflow.getAcl().getGroupEntries().size() ,0 );
        Assert.assertEquals(workflow.getAcl().getUserEntries().size() , 3);
        Assert.assertEquals(workflow.getAcl().getUserEntries().get(user).getPermission() , ACLPermission.FORBIDDEN);
        Assert.assertEquals(workflow.getAcl().getUserEntries().get(user2).getPermission() , ACLPermission.READ_ONLY);
        Assert.assertEquals(workflow.getAcl().getUserEntries().get(user3).getPermission() , ACLPermission.FULL_ACCESS);


    }

    @Test
    public void testUpdateACLForWorkflowWithAnExistingACL() throws Exception {
        //Given
        Map<String, String> userEntries = new HashMap<>();
        Map<String, String> grpEntries = new HashMap<>();
        User user2 = new User(workspace,new Account(WorkflowUtil.USER2_LOGIN , WorkflowUtil.USER2_NAME,WorkflowUtil.USER2_MAIL, "en", new Date(), null));
        User user3 = new User(workspace,new Account(WorkflowUtil.USER3_LOGIN , WorkflowUtil.USER3_NAME,WorkflowUtil.USER3_MAIL, "en", new Date(), null));
        UserGroup group1 = new UserGroup(workspace,WorkflowUtil.GRP1_ID);

        WorkflowModel workflowModel = new WorkflowModel(workspace, WorkflowUtil.WORKSPACE_ID, user, "");
        ACL acl = new ACL();
        // user2 had READ_ONLY access in the existing acl
        acl.addEntry(user2, ACLPermission.READ_ONLY);
        acl.addEntry(group1, ACLPermission.FULL_ACCESS);
        workflowModel.setAcl(acl);

        userEntries.put(user.getLogin(), ACLPermission.FORBIDDEN.name());
        // user2 has non access FORBIDDEN in the new acl
        userEntries.put(user2.getLogin(), ACLPermission.FORBIDDEN.name());
        userEntries.put(user3.getLogin(), ACLPermission.FULL_ACCESS.name());


        //user2 belong to group1
        group1.addUser(user2);
        group1.addUser(user);
        //group1 has FULL_ACCESS
        grpEntries.put(group1.getId(),ACLPermission.FULL_ACCESS.name());


        // User has read access to the workspace
        Mockito.when(userManager.checkWorkspaceReadAccess(WorkflowUtil.WORKSPACE_ID)).thenReturn(user);
        Mockito.when(em.find(WorkflowModel.class, new WorkflowModelKey(WorkflowUtil.WORKSPACE_ID, WorkflowUtil.WORKFLOW_MODEL_ID))).thenReturn(workflowModel);
        Mockito.when(em.find(User.class, new UserKey(WorkflowUtil.WORKSPACE_ID,WorkflowUtil.USER_LOGIN))).thenReturn(user);
        Mockito.when(em.find(User.class, new UserKey(WorkflowUtil.WORKSPACE_ID,WorkflowUtil.USER2_LOGIN))).thenReturn(user2);
        Mockito.when(em.find(User.class, new UserKey(WorkflowUtil.WORKSPACE_ID,WorkflowUtil.USER3_LOGIN))).thenReturn(user3);
        Mockito.when(em.getReference(UserGroup.class, new UserGroupKey(WorkflowUtil.WORKSPACE_ID, group1.getId()))).thenReturn(group1);
        Mockito.when(em.getReference(User.class, user.getKey())).thenReturn(user);
        Mockito.when(em.getReference(User.class, user2.getKey())).thenReturn(user2);
        Mockito.when(em.getReference(User.class, user3.getKey())).thenReturn(user3);
        Mockito.when(aclTypedQuery.setParameter(Matchers.anyString(),Matchers.any())).thenReturn(aclTypedQuery);
        Mockito.when(em.createNamedQuery(Matchers.<String>any())).thenReturn(aclTypedQuery);

          //When
        WorkflowModel workflow= workflowManagerBean.updateACLForWorkflow(WorkflowUtil.WORKSPACE_ID, WorkflowUtil.WORKFLOW_MODEL_ID, userEntries, grpEntries);
        //Then
        Assert.assertEquals(workflow.getAcl().getGroupEntries().size(),1 );
        Assert.assertEquals(workflow.getAcl().getUserEntries().size() , 3);
        Assert.assertEquals(workflow.getAcl().getUserEntries().get(user).getPermission() , ACLPermission.FORBIDDEN);
        Assert.assertEquals(workflow.getAcl().getUserEntries().get(user2).getPermission() , ACLPermission.FORBIDDEN);
        Assert.assertEquals(workflow.getAcl().getUserEntries().get(user3).getPermission() , ACLPermission.FULL_ACCESS);

    }


}

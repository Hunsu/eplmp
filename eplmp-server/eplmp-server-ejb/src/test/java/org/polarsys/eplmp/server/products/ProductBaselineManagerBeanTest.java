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

package org.polarsys.eplmp.server.products;

import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.configuration.ProductBaseline;
import org.polarsys.eplmp.core.configuration.ProductBaselineType;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.product.ConfigurationItem;
import org.polarsys.eplmp.core.product.PartIteration;
import org.polarsys.eplmp.core.product.PathToPathLink;
import org.polarsys.eplmp.server.BinaryStorageManagerBean;
import org.polarsys.eplmp.server.ProductManagerBean;
import org.polarsys.eplmp.server.UserManagerBean;
import org.polarsys.eplmp.server.dao.ConfigurationItemDAO;
import org.polarsys.eplmp.server.dao.PartIterationDAO;
import org.polarsys.eplmp.server.util.BaselineRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import javax.ejb.SessionContext;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Locale;

import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
public class ProductBaselineManagerBeanTest {

    @InjectMocks
    ProductBaselineManagerBean productBaselineService = new ProductBaselineManagerBean();
    @Mock
    SessionContext ctx;
    @Mock
    Principal principal;
    @Mock
    UserManagerBean userManager;
    @Mock
    EntityManager em;
    @Mock
    BinaryStorageManagerBean dataManager;
    @Mock
    ProductManagerBean productService;

    @Rule
    public BaselineRule baselineRuleNotReleased;
    @Rule
    public BaselineRule baselineRuleReleased;
    @Rule
    public BaselineRule baselineRuleLatest;
    @Mock
    private TypedQuery<PathToPathLink> mockedQuery;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup() throws Exception {
        initMocks(this);
        Mockito.when(ctx.getCallerPrincipal()).thenReturn(principal);
        Mockito.when(principal.getName()).thenReturn("user1");
    }


    /**
     * test the creation of Released baseline
     */
    @Test
    public void createReleasedBaseline() throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, ConfigurationItemNotFoundException, NotAllowedException, UserNotActiveException, PartIterationNotFoundException, PartRevisionNotReleasedException, EntityConstraintException, PartMasterNotFoundException, CreationException, BaselineNotFoundException, PathToPathLinkAlreadyExistsException, WorkspaceNotEnabledException {

        //Given
        baselineRuleReleased = new BaselineRule("myBaseline", ProductBaselineType.RELEASED, "description", "workspace01", "user1", "part01", "product01", true);
        doReturn(new User()).when(userManager).checkWorkspaceWriteAccess(Matchers.anyString());
        Mockito.when(userManager.checkWorkspaceWriteAccess(Matchers.anyString())).thenReturn(baselineRuleReleased.getUser());
        Mockito.when(em.find(ConfigurationItem.class, baselineRuleReleased.getConfigurationItemKey())).thenReturn(baselineRuleReleased.getConfigurationItem());
        Mockito.when(new ConfigurationItemDAO(new Locale("en"), em).loadConfigurationItem(baselineRuleReleased.getConfigurationItemKey())).thenReturn(baselineRuleReleased.getConfigurationItem());

        Mockito.when(productService.getRootPartUsageLink(Matchers.any())).thenReturn(baselineRuleReleased.getRootPartUsageLink());
        Mockito.when(mockedQuery.setParameter(Matchers.anyString(), Matchers.any())).thenReturn(mockedQuery);
        Mockito.when(em.createNamedQuery("PathToPathLink.findPathToPathLinkByPathListInProduct", PathToPathLink.class)).thenReturn(mockedQuery);

        //When
        ProductBaseline baseline = productBaselineService.createBaseline(baselineRuleReleased.getConfigurationItemKey(), baselineRuleReleased.getName(), baselineRuleReleased.getType(), baselineRuleReleased.getDescription(), new ArrayList<>(), baselineRuleReleased.getSubstituteLinks(), baselineRuleReleased.getOptionalUsageLinks());

        //Then
        Assert.assertTrue(baseline != null);
        Assert.assertTrue(baseline.getDescription().equals(baselineRuleReleased.getDescription()));
        Assert.assertTrue(baseline.getType().equals(baselineRuleReleased.getType()));
        Assert.assertTrue(baseline.getConfigurationItem().getWorkspaceId().equals(baselineRuleReleased.getWorkspace().getId()));

    }

    /**
     * test the creation of a released baseline with a product that contains a part that has not been released yet
     *
     * @throws Exception PartRevisionNotReleasedException
     */
    @Test
    public void createReleasedBaselineUsingPartNotReleased() throws Exception{

        //Given
        baselineRuleNotReleased = new BaselineRule("myBaseline", ProductBaselineType.RELEASED, "description", "workspace01", "user1", "part01", "product01", false);

        doReturn(new User()).when(userManager).checkWorkspaceWriteAccess(Matchers.anyString());
        Mockito.when(userManager.checkWorkspaceWriteAccess(Matchers.anyString())).thenReturn(baselineRuleNotReleased.getUser());
        Mockito.when(em.find(ConfigurationItem.class, baselineRuleNotReleased.getConfigurationItemKey())).thenReturn(baselineRuleNotReleased.getConfigurationItem());
        Mockito.when(new ConfigurationItemDAO(new Locale("en"), em).loadConfigurationItem(baselineRuleNotReleased.getConfigurationItemKey())).thenReturn(baselineRuleNotReleased.getConfigurationItem());
        thrown.expect(NotAllowedException.class);
        //When
        productBaselineService.createBaseline(baselineRuleNotReleased.getConfigurationItemKey(), baselineRuleNotReleased.getName(), baselineRuleNotReleased.getType(), baselineRuleNotReleased.getDescription(),new ArrayList<>(), baselineRuleNotReleased.getSubstituteLinks(), baselineRuleNotReleased.getOptionalUsageLinks());

    }
    /**
     * test the creation of latest baseline
     */
    @Test
    public void createLatestBaseline() throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, ConfigurationItemNotFoundException, EntityConstraintException, UserNotActiveException, NotAllowedException, PartIterationNotFoundException, PartRevisionNotReleasedException, PartMasterNotFoundException, CreationException, BaselineNotFoundException, PathToPathLinkAlreadyExistsException, WorkspaceNotEnabledException {

        //Given
        baselineRuleLatest = new BaselineRule("myBaseline", ProductBaselineType.LATEST, "description", "workspace01", "user1", "part01", "product01", true);
        doReturn(new User()).when(userManager).checkWorkspaceWriteAccess(Matchers.anyString());
        Mockito.when(userManager.checkWorkspaceWriteAccess(Matchers.anyString())).thenReturn(baselineRuleLatest.getUser());
        Mockito.when(em.find(ConfigurationItem.class, baselineRuleLatest.getConfigurationItemKey())).thenReturn(baselineRuleLatest.getConfigurationItem());
        Mockito.when(new ConfigurationItemDAO(new Locale("en"), em).loadConfigurationItem(baselineRuleLatest.getConfigurationItemKey())).thenReturn(baselineRuleLatest.getConfigurationItem());

        Mockito.when(productService.getRootPartUsageLink(Matchers.any())).thenReturn(baselineRuleLatest.getRootPartUsageLink());
        Mockito.when(mockedQuery.setParameter(Matchers.anyString(), Matchers.any())).thenReturn(mockedQuery);
        Mockito.when(em.createNamedQuery("PathToPathLink.findPathToPathLinkByPathListInProduct", PathToPathLink.class)).thenReturn(mockedQuery);

        //When
        ProductBaseline baseline = productBaselineService.createBaseline(baselineRuleLatest.getConfigurationItemKey(), baselineRuleLatest.getName(), baselineRuleLatest.getType(), baselineRuleLatest.getDescription(), new ArrayList<>(), baselineRuleLatest.getSubstituteLinks(), baselineRuleLatest.getOptionalUsageLinks());

        //Then
        Assert.assertTrue(baseline != null);
        Assert.assertTrue(baseline.getDescription().equals(baselineRuleLatest.getDescription()));
        Assert.assertTrue(baseline.getType().equals(baselineRuleLatest.getType()));
        Assert.assertTrue(baseline.getConfigurationItem().getWorkspaceId().equals(baselineRuleLatest.getWorkspace().getId()));

    }

    /**
     * @throws UserNotFoundException
     * @throws AccessRightException
     * @throws WorkspaceNotFoundException
     * @throws ConfigurationItemNotFoundException
     * @throws NotAllowedException
     * @throws UserNotActiveException
     * @throws PartIterationNotFoundException
     * @throws org.polarsys.eplmp.core.exceptions.PartRevisionNotReleasedException
     */
    @Test
    public void createLatestBaselineWithCheckedPart() throws UserNotFoundException, AccessRightException, WorkspaceNotFoundException, ConfigurationItemNotFoundException, NotAllowedException, UserNotActiveException, PartIterationNotFoundException, PartRevisionNotReleasedException, EntityConstraintException, PartMasterNotFoundException, CreationException, BaselineNotFoundException, PathToPathLinkAlreadyExistsException, WorkspaceNotEnabledException {

        //Given
        baselineRuleReleased = new BaselineRule("myBaseline", ProductBaselineType.LATEST , "description", "workspace01", "user1", "part01", "product01", true, false);
        doReturn(new User()).when(userManager).checkWorkspaceWriteAccess(Matchers.anyString());
        Mockito.when(userManager.checkWorkspaceWriteAccess(Matchers.anyString())).thenReturn(baselineRuleReleased.getUser());
        Mockito.when(em.find(ConfigurationItem.class, baselineRuleReleased.getConfigurationItemKey())).thenReturn(baselineRuleReleased.getConfigurationItem()
        );
        Mockito.when(new ConfigurationItemDAO(new Locale("en"), em).loadConfigurationItem(baselineRuleReleased.getConfigurationItemKey())).thenReturn(baselineRuleReleased.getConfigurationItem());
        Mockito.when(em.find(PartIteration.class, baselineRuleReleased.getPartMaster().getLastReleasedRevision().getIteration(1).getKey())).thenReturn(baselineRuleReleased.getPartMaster().getLastReleasedRevision().getIteration(1));
        Mockito.when(new PartIterationDAO(new Locale("en"), em).loadPartI(baselineRuleReleased.getPartMaster().getLastReleasedRevision().getIteration(1).getKey())).thenReturn(baselineRuleReleased.getPartMaster().getLastReleasedRevision().getIteration(1));
        Mockito.when(productService.getRootPartUsageLink(Matchers.any())).thenReturn(baselineRuleReleased.getRootPartUsageLink());
        Mockito.when(mockedQuery.setParameter(Matchers.anyString(), Matchers.any())).thenReturn(mockedQuery);
        Mockito.when(em.createNamedQuery("PathToPathLink.findPathToPathLinkByPathListInProduct", PathToPathLink.class)).thenReturn(mockedQuery);

        //When
        ProductBaseline baseline = productBaselineService.createBaseline(baselineRuleReleased.getConfigurationItemKey(), baselineRuleReleased.getName(), baselineRuleReleased.getType(), baselineRuleReleased.getDescription(), new ArrayList<>(), baselineRuleReleased.getSubstituteLinks(), baselineRuleReleased.getOptionalUsageLinks());

        //Then
        Assert.assertTrue(baseline != null);
        Assert.assertTrue(baseline.getDescription().equals(baselineRuleReleased.getDescription()));
        Assert.assertTrue(baseline.getType().equals(ProductBaselineType.LATEST));
        Assert.assertTrue(baseline.getConfigurationItem().getWorkspaceId().equals(baselineRuleReleased.getWorkspace().getId()));

    }

}

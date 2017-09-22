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

import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.exceptions.*;
import org.polarsys.eplmp.core.product.*;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.*;
import org.polarsys.eplmp.server.dao.ConfigurationItemDAO;
import org.polarsys.eplmp.server.dao.EffectivityDAO;
import org.polarsys.eplmp.server.dao.PartRevisionDAO;

import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

@DeclareRoles({UserGroupMapping.REGULAR_USER_ROLE_ID})
@Local(IEffectivityManagerLocal.class)
@Stateless(name = "EffectivityManagerBean")
public class EffectivityManagerBean implements IEffectivityManagerLocal {

    @PersistenceContext
    private EntityManager em;

    @Inject
    private IAccountManagerLocal accountManager;


    @Inject
    private IUserManagerLocal userManager;


    @Inject
    private IProductManagerLocal productManager;


    @Override
    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    public SerialNumberBasedEffectivity createSerialNumberBasedEffectivity(String workspaceId, String partNumber, String version, String pName, String pDescription, String pConfigurationItemId, String pStartNumber, String pEndNumber)
            throws EffectivityAlreadyExistsException, CreationException, ConfigurationItemNotFoundException, UserNotFoundException, WorkspaceNotFoundException, WorkspaceNotEnabledException, AccessRightException, PartRevisionNotFoundException, UserNotActiveException {

        User user = userManager.checkWorkspaceWriteAccess(workspaceId);
        Locale locale = new Locale(user.getLanguage());

        // lower range is mandatory, upper range isn't
        if (pStartNumber == null || pStartNumber.isEmpty()) {
            throw new CreationException(locale);
        }

        PartRevisionKey partRevisionKey = new PartRevisionKey(workspaceId, partNumber, version);
        ConfigurationItemKey configurationItemKey = new ConfigurationItemKey(workspaceId, pConfigurationItemId);

        ConfigurationItem configurationItem =
                new ConfigurationItemDAO(locale, em).loadConfigurationItem(configurationItemKey);

        EffectivityDAO effectivityDAO = new EffectivityDAO(locale, em);
        SerialNumberBasedEffectivity serialNumberBasedEffectivity = new SerialNumberBasedEffectivity();
        serialNumberBasedEffectivity.setName(pName);
        serialNumberBasedEffectivity.setDescription(pDescription);
        serialNumberBasedEffectivity.setConfigurationItem(configurationItem);
        serialNumberBasedEffectivity.setStartNumber(pStartNumber);
        serialNumberBasedEffectivity.setEndNumber(pEndNumber);


        effectivityDAO.createEffectivity(serialNumberBasedEffectivity);

        PartRevision partRevision = productManager.getPartRevision(partRevisionKey);
        Set<Effectivity> effectivities = partRevision.getEffectivities();
        effectivities.add(serialNumberBasedEffectivity);
        partRevision.setEffectivities(effectivities);
        PartRevisionDAO partRevisionDAO = new PartRevisionDAO(em);
        partRevisionDAO.updateRevision(partRevision);

        return serialNumberBasedEffectivity;
    }

    @Override
    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    public DateBasedEffectivity createDateBasedEffectivity(
            String workspaceId, String partNumber, String version, String pName, String pDescription, String pConfigurationItemId, Date pStartDate, Date pEndDate)
            throws EffectivityAlreadyExistsException, CreationException, UserNotFoundException, WorkspaceNotFoundException, WorkspaceNotEnabledException, AccessRightException, PartRevisionNotFoundException, UserNotActiveException, ConfigurationItemNotFoundException {

        User user = userManager.checkWorkspaceWriteAccess(workspaceId);
        Locale locale = new Locale(user.getLanguage());

        // lower range is mandatory, upper range isn't
        if (pStartDate == null) {
            throw new CreationException(locale);
        }

        // ConfigurationItem is optional for Date based effectivities
        ConfigurationItem configurationItem = null;

        if (pConfigurationItemId != null && !pConfigurationItemId.isEmpty()) {
            ConfigurationItemKey configurationItemKey = new ConfigurationItemKey(workspaceId, pConfigurationItemId);
            configurationItem = new ConfigurationItemDAO(locale, em).loadConfigurationItem(configurationItemKey);
        }

        EffectivityDAO effectivityDAO = new EffectivityDAO(locale, em);
        DateBasedEffectivity dateBasedEffectivity = new DateBasedEffectivity();
        dateBasedEffectivity.setName(pName);
        dateBasedEffectivity.setDescription(pDescription);
        dateBasedEffectivity.setStartDate(pStartDate);
        dateBasedEffectivity.setEndDate(pEndDate);
        dateBasedEffectivity.setConfigurationItem(configurationItem);
        effectivityDAO.createEffectivity(dateBasedEffectivity);

        PartRevisionKey partRevisionKey = new PartRevisionKey(workspaceId, partNumber, version);
        PartRevision partRevision = productManager.getPartRevision(partRevisionKey);
        Set<Effectivity> effectivities = partRevision.getEffectivities();
        effectivities.add(dateBasedEffectivity);
        partRevision.setEffectivities(effectivities);
        PartRevisionDAO partRevisionDAO = new PartRevisionDAO(em);
        partRevisionDAO.updateRevision(partRevision);
        return dateBasedEffectivity;
    }

    @Override
    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    public LotBasedEffectivity createLotBasedEffectivity(
            String workspaceId, String partNumber, String version, String pName, String pDescription, String pConfigurationItemId, String pStartLotId, String pEndLotId) throws UserNotFoundException, WorkspaceNotFoundException, WorkspaceNotEnabledException, AccessRightException, CreationException, EffectivityAlreadyExistsException, ConfigurationItemNotFoundException, PartRevisionNotFoundException, UserNotActiveException {


        User user = userManager.checkWorkspaceWriteAccess(workspaceId);
        Locale locale = new Locale(user.getLanguage());

        // lower range is mandatory, upper range isn't
        if (pStartLotId == null || pStartLotId.isEmpty()) {
            throw new CreationException(locale);
        }

        ConfigurationItemKey configurationItemKey = new ConfigurationItemKey(workspaceId, pConfigurationItemId);
        ConfigurationItem configurationItem = new ConfigurationItemDAO(locale, em).loadConfigurationItem(configurationItemKey);

        EffectivityDAO effectivityDAO = new EffectivityDAO(em);
        LotBasedEffectivity lotBasedEffectivity = new LotBasedEffectivity();
        lotBasedEffectivity.setName(pName);
        lotBasedEffectivity.setDescription(pDescription);
        lotBasedEffectivity.setConfigurationItem(configurationItem);
        lotBasedEffectivity.setStartLotId(pStartLotId);
        lotBasedEffectivity.setEndLotId(pEndLotId);
        effectivityDAO.createEffectivity(lotBasedEffectivity);

        PartRevisionKey partRevisionKey = new PartRevisionKey(workspaceId, partNumber, version);
        PartRevision partRevision = productManager.getPartRevision(partRevisionKey);

        Set<Effectivity> effectivities = partRevision.getEffectivities();
        effectivities.add(lotBasedEffectivity);
        partRevision.setEffectivities(effectivities);
        PartRevisionDAO partRevisionDAO = new PartRevisionDAO(em);
        partRevisionDAO.updateRevision(partRevision);

        return lotBasedEffectivity;
    }

    @Override
    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    public Effectivity getEffectivity(String workspaceId, int pId) throws EffectivityNotFoundException, UserNotFoundException, WorkspaceNotFoundException, UserNotActiveException, WorkspaceNotEnabledException {
        User user = userManager.checkWorkspaceReadAccess(workspaceId);
        Locale locale = new Locale(user.getLanguage());
        EffectivityDAO effectivityDAO = new EffectivityDAO(locale, em);
        PartRevision partRevision = effectivityDAO.getPartRevisionHolder(pId);

        if (partRevision == null || !partRevision.getWorkspaceId().equals(workspaceId)) {
            throw new EffectivityNotFoundException(locale, String.valueOf(pId));
        }

        return partRevision.getEffectivities().stream()
                .filter(e -> e.getId() == pId).findFirst().orElse(null);
    }

    @Override
    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    public Effectivity updateEffectivity(String workspaceId, int pId, String pName, String pDescription) throws EffectivityNotFoundException, UserNotFoundException, WorkspaceNotFoundException, WorkspaceNotEnabledException, AccessRightException {

        User user = userManager.checkWorkspaceWriteAccess(workspaceId);
        Locale locale = new Locale(user.getLanguage());
        EffectivityDAO effectivityDAO = new EffectivityDAO(locale, em);

        PartRevision partRevision = effectivityDAO.getPartRevisionHolder(pId);

        if (partRevision == null || !partRevision.getWorkspaceId().equals(workspaceId)) {
            throw new EffectivityNotFoundException(locale, String.valueOf(pId));
        }

        Effectivity effectivity = partRevision.getEffectivities().stream()
                .filter(e -> e.getId() == pId).findFirst().orElse(null);

        effectivity.setName(pName);
        effectivity.setDescription(pDescription);
        effectivityDAO.updateEffectivity(effectivity);
        return effectivity;
    }

    @Override
    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    public SerialNumberBasedEffectivity updateSerialNumberBasedEffectivity(String workspaceId, int pId, String pName, String pDescription, String pStartNumber, String pEndNumber) throws EffectivityNotFoundException, UpdateException, UserNotFoundException, WorkspaceNotFoundException, WorkspaceNotEnabledException, AccessRightException, CreationException {

        User user = userManager.checkWorkspaceWriteAccess(workspaceId);
        Locale locale = new Locale(user.getLanguage());

        // lower range is mandatory, upper range isn't
        if (pStartNumber == null || pStartNumber.isEmpty()) {
            throw new CreationException(locale);
        }

        EffectivityDAO effectivityDAO = new EffectivityDAO(locale, em);

        PartRevision partRevision = effectivityDAO.getPartRevisionHolder(pId);

        if (partRevision == null || !partRevision.getWorkspaceId().equals(workspaceId)) {
            throw new EffectivityNotFoundException(locale, String.valueOf(pId));
        }

        SerialNumberBasedEffectivity effectivity = (SerialNumberBasedEffectivity) partRevision.getEffectivities().stream()
                .filter(e -> e.getId() == pId).findFirst().orElse(null);

        effectivity.setName(pName);
        effectivity.setDescription(pDescription);
        effectivity.setStartNumber(pStartNumber);
        effectivity.setEndNumber(pEndNumber);
        effectivityDAO.updateEffectivity(effectivity);

        return effectivity;
    }

    @Override
    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    public DateBasedEffectivity updateDateBasedEffectivity(String workspaceId, int pId, String pName, String pDescription, Date pStartDate, Date pEndDate) throws EffectivityNotFoundException, UpdateException, UserNotFoundException, WorkspaceNotFoundException, WorkspaceNotEnabledException, AccessRightException, CreationException {

        User user = userManager.checkWorkspaceWriteAccess(workspaceId);
        Locale locale = new Locale(user.getLanguage());

        // lower range is mandatory, upper range isn't
        if (pStartDate == null) {
            throw new CreationException(locale);
        }

        EffectivityDAO effectivityDAO = new EffectivityDAO(locale, em);

        PartRevision partRevision = effectivityDAO.getPartRevisionHolder(pId);

        if (partRevision == null || !partRevision.getWorkspaceId().equals(workspaceId)) {
            throw new EffectivityNotFoundException(locale, String.valueOf(pId));
        }

        DateBasedEffectivity effectivity = (DateBasedEffectivity) partRevision.getEffectivities().stream()
                .filter(e -> e.getId() == pId).findFirst().orElse(null);

        effectivity.setName(pName);
        effectivity.setDescription(pDescription);
        effectivity.setStartDate(pStartDate);
        effectivity.setEndDate(pEndDate);
        effectivityDAO.updateEffectivity(effectivity);

        return effectivity;
    }

    @Override
    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    public LotBasedEffectivity updateLotBasedEffectivity(String workspaceId, int pId, String pName, String pDescription, String pStartLotId, String pEndLotId) throws UserNotFoundException, WorkspaceNotFoundException, WorkspaceNotEnabledException, AccessRightException, CreationException, EffectivityNotFoundException {

        User user = userManager.checkWorkspaceWriteAccess(workspaceId);
        Locale locale = new Locale(user.getLanguage());

        // lower range is mandatory, upper range isn't
        if (pStartLotId == null || pStartLotId.isEmpty()) {
            throw new CreationException(locale);
        }

        EffectivityDAO effectivityDAO = new EffectivityDAO(locale, em);

        PartRevision partRevision = effectivityDAO.getPartRevisionHolder(pId);

        if (partRevision == null || !partRevision.getWorkspaceId().equals(workspaceId)) {
            throw new EffectivityNotFoundException(locale, String.valueOf(pId));
        }

        LotBasedEffectivity effectivity = (LotBasedEffectivity) partRevision.getEffectivities().stream()
                .filter(e -> e.getId() == pId).findFirst().orElse(null);

        effectivity.setName(pName);
        effectivity.setDescription(pDescription);
        effectivity.setStartLotId(pStartLotId);
        effectivity.setEndLotId(pEndLotId);
        effectivityDAO.updateEffectivity(effectivity);

        return effectivity;
    }

    @Override
    @RolesAllowed(UserGroupMapping.REGULAR_USER_ROLE_ID)
    public void deleteEffectivity(String workspaceId, String partNumber, String version, int pId) throws EffectivityNotFoundException, UserNotFoundException, WorkspaceNotFoundException, WorkspaceNotEnabledException, AccessRightException, PartRevisionNotFoundException, UserNotActiveException {

        User user = userManager.checkWorkspaceWriteAccess(workspaceId);
        Locale locale = new Locale(user.getLanguage());
        EffectivityDAO effectivityDAO = new EffectivityDAO(locale, em);

        PartRevision partRevision = effectivityDAO.getPartRevisionHolder(pId);

        Effectivity effectivity = partRevision.getEffectivities().stream()
                .filter(e -> e.getId() == pId).findFirst().orElse(null);

        if (effectivity == null || !partRevision.getWorkspaceId().equals(workspaceId)) {
            throw new EffectivityNotFoundException(locale, String.valueOf(pId));
        }

        PartRevisionDAO partRevisionDAO = new PartRevisionDAO(locale, em);
        partRevisionDAO.removePartRevisionEffectivity(partRevision, effectivity);

        effectivityDAO.removeEffectivity(effectivity);
    }
}

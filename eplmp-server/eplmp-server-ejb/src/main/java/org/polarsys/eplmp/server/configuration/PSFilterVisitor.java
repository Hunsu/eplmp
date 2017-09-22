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

package org.polarsys.eplmp.server.configuration;

import org.polarsys.eplmp.core.common.User;
import org.polarsys.eplmp.core.configuration.ProductStructureFilter;
import org.polarsys.eplmp.core.exceptions.EntityConstraintException;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.exceptions.PartMasterNotFoundException;
import org.polarsys.eplmp.core.product.*;
import org.polarsys.eplmp.server.dao.PartMasterDAO;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;


public abstract class PSFilterVisitor {

    private static final Logger LOGGER = Logger.getLogger(PSFilterVisitor.class.getName());

    private User user;
    private Locale locale;
    private String workspaceId;
    private ProductStructureFilter filter;
    private EntityManager em;
    private PartMasterDAO partMasterDAO;
    private Component component;
    private int stopAtDepth = -1;
    private boolean stopped = false;

    public PSFilterVisitor(EntityManager pEm, User pUser, ProductStructureFilter pFilter)
            throws PartMasterNotFoundException, NotAllowedException, EntityConstraintException {

        filter = pFilter;
        em = pEm;
        user = pUser;
        workspaceId = user.getWorkspaceId();
        locale = new Locale(user.getLanguage());
        partMasterDAO = new PartMasterDAO(locale, em);
    }

    /**
     * Start the visitor with given part master
     * */
    public void visit(PartMaster pNodeFrom, Integer pStopAtDepth) throws PartMasterNotFoundException, EntityConstraintException, NotAllowedException {

        setDepth(pStopAtDepth);
        List<PartLink> currentPath = new ArrayList<>();
        List<PartMaster> currentPathParts = new ArrayList<>();
        List<PartIteration> currentPathPartIterations = new ArrayList<>();

        PartLink virtualLink = createVirtualRootLink(pNodeFrom);
        currentPathParts.add(pNodeFrom);
        currentPath.add(virtualLink);

        component = new Component(pNodeFrom.getAuthor(),pNodeFrom,currentPath,null);
        List<Component> result = getComponentsRecursively(component, currentPathPartIterations, currentPathParts, currentPath);
        component.setComponents(result);

    }

    /**
     * Start the visitor with given path
     * */
    public void visit(List<PartLink> pStartingPath, Integer pStopAtDepth) throws PartMasterNotFoundException, EntityConstraintException, NotAllowedException {

        setDepth(pStopAtDepth);
        List<PartLink> currentPath = pStartingPath;
        List<PartMaster> currentPathParts = new ArrayList<>();
        List<PartIteration> currentPathPartIterations = new ArrayList<>();

        PartMaster rootNode = currentPath.get(currentPath.size() - 1).getComponent();
        currentPathParts.add(rootNode);

        component = new Component(rootNode.getAuthor(),rootNode,currentPath,null);
        List<Component> result = getComponentsRecursively(component, currentPathPartIterations, currentPathParts, currentPath);
        component.setComponents(result);
    }

    public void stop(){
        stopped = true;
    }

    private void setDepth(Integer pDepth){
        stopAtDepth = pDepth == null ? -1 : pDepth;
    }

    private List<Component> getComponentsRecursively(Component currentComponent, List<PartIteration> pCurrentPathPartIterations, List<PartMaster> pCurrentPathParts, List<PartLink> pCurrentPath) throws PartMasterNotFoundException, NotAllowedException, EntityConstraintException {
        List<Component> components = new ArrayList<>();

        if(stopped){
            return components;
        }

        if(!onPathWalk(new ArrayList<>(pCurrentPath), new ArrayList<>(pCurrentPathParts))) {
            return components;
        }

        // Current depth
        int currentDepth = pCurrentPathParts.size();

        // Current part master is the last from pCurrentPathParts
        PartMaster currentUsagePartMaster = pCurrentPathParts.get(pCurrentPathParts.size()-1);

        // Find filtered iterations to visit
        List<PartIteration> partIterations = filter.filter(currentUsagePartMaster);

        if(partIterations.isEmpty()){
            onUnresolvedVersion(currentUsagePartMaster);
        }

        if(partIterations.size() > 1){
            onIndeterminateVersion(currentUsagePartMaster, new ArrayList<>(partIterations));
        }

        if(partIterations.size()==1){
            currentComponent.setRetainedIteration(partIterations.get(0));
        }

        // Visit them all, potentially diverging branches
        for (PartIteration partIteration : partIterations) {

            // We know which iteration of current partMaster, add it to list
            List<PartIteration> copyPartIteration = new ArrayList<>(pCurrentPathPartIterations);
            copyPartIteration.add(partIteration);

            // Is branch over ?
            if(partIteration.getComponents().isEmpty()){
                onBranchDiscovered(new ArrayList<>(pCurrentPath),new ArrayList<>(copyPartIteration));
            }

            // Navigate links
            for (PartUsageLink usageLink : partIteration.getComponents()) {

                List<PartLink> currentPath = new ArrayList<>(pCurrentPath);
                currentPath.add(usageLink);

                // Filter the current path, potentially diverging branches
                List<PartLink> eligiblePath = filter.filter(currentPath);

                if(eligiblePath.isEmpty() && !usageLink.isOptional()){
                    onUnresolvedPath(new ArrayList<>(currentPath), new ArrayList<>(copyPartIteration));
                }

                if(eligiblePath.size() > 1 ){
                    onIndeterminatePath(new ArrayList<>(currentPath), new ArrayList<>(copyPartIteration));
                }

                if (eligiblePath.size() == 1 && eligiblePath.get(0).isOptional()){
                    onOptionalPath(new ArrayList<>(currentPath), new ArrayList<>(copyPartIteration));
                }

                for(PartLink link : eligiblePath){
                    List<PartLink> nextPath = new ArrayList<>(pCurrentPath);
                    nextPath.add(link);

                    if (stopAtDepth == -1 || stopAtDepth >= currentDepth) {

                        // Going on a new path
                        PartMaster pm = loadPartMaster(link.getComponent().getNumber());

                        // Run cyclic integrity check here
                        if(pCurrentPathParts.contains(pm)){
                            throw new EntityConstraintException(locale,"EntityConstraintException12");
                        }

                        // Continue tree walking on pm
                        List<PartMaster> copyPathParts = new ArrayList<>(pCurrentPathParts);
                        List<PartLink> copyPath = new ArrayList<>(nextPath);
                        List<PartIteration> copyPartIterations = new ArrayList<>(copyPartIteration);
                        copyPathParts.add(pm);

                        // Recursive
                        Component subComponent= new Component(pm.getAuthor(), pm, copyPath, null);
                        subComponent.setComponents(getComponentsRecursively(subComponent, copyPartIterations, copyPathParts, copyPath));
                        components.add(subComponent);
                    }

                }

            }
        }

        return components;
    }



    private PartMaster loadPartMaster(String partNumber) throws PartMasterNotFoundException {
        return partMasterDAO.loadPartM(new PartMasterKey(workspaceId, partNumber));
    }

    private PartLink createVirtualRootLink(PartMaster pNodeFrom) {

        return new PartLink() {
            @Override
            public int getId() {
                return 1;
            }

            @Override
            public double getAmount() {
                return 1;
            }

            @Override
            public String getUnit() {
                return null;
            }

            @Override
            public String getComment() {
                return "";
            }

            @Override
            public boolean isOptional() {
                return false;
            }

            @Override
            public PartMaster getComponent() {
                return pNodeFrom;
            }

            @Override
            public List<PartSubstituteLink> getSubstitutes() {
                return null;
            }

            @Override
            public String getReferenceDescription() {
                return null;
            }

            @Override
            public Character getCode() {
                return '-';
            }

            @Override
            public String getFullId() {
                return "-1";
            }

            @Override
            public List<CADInstance> getCadInstances() {
                return null;
            }
        };
    }

    /**
     * Getters
     */
    public Component getComponent() {
        return component;
    }

    /**
     * Abstracts
     * */

    public abstract void onIndeterminateVersion(PartMaster partMaster, List<PartIteration> partIterations) throws NotAllowedException;
    public abstract void onUnresolvedVersion(PartMaster partMaster) throws NotAllowedException;
    public abstract void onIndeterminatePath(List<PartLink> pCurrentPath, List<PartIteration> pCurrentPathPartIterations) throws NotAllowedException;
    public abstract void onUnresolvedPath(List<PartLink> pCurrentPath, List<PartIteration> partIterations) throws NotAllowedException;
    public abstract void onBranchDiscovered(List<PartLink> pCurrentPath, List<PartIteration> copyPartIteration);
    public abstract void onOptionalPath(List<PartLink> path, List<PartIteration> partIterations);
    public abstract boolean onPathWalk(List<PartLink> path, List<PartMaster> parts);
}

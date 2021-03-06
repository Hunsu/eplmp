/*******************************************************************************
 * Copyright (c) 2017 DocDoku.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p>
 * Contributors:
 * DocDoku - initial API and implementation
 *******************************************************************************/
package org.polarsys.eplmp.server.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.dozer.DozerBeanMapperSingletonWrapper;
import org.dozer.Mapper;

import org.polarsys.eplmp.core.common.Account;
import org.polarsys.eplmp.core.common.Workspace;
import org.polarsys.eplmp.core.exceptions.AccountAlreadyExistsException;
import org.polarsys.eplmp.core.exceptions.AccountNotFoundException;
import org.polarsys.eplmp.core.exceptions.CreationException;
import org.polarsys.eplmp.core.exceptions.EntityAlreadyExistsException;
import org.polarsys.eplmp.core.exceptions.EntityNotFoundException;
import org.polarsys.eplmp.core.exceptions.NotAllowedException;
import org.polarsys.eplmp.core.security.UserGroupMapping;
import org.polarsys.eplmp.core.services.IAccountManagerLocal;
import org.polarsys.eplmp.core.services.IContextManagerLocal;
import org.polarsys.eplmp.core.services.IOAuthManagerLocal;
import org.polarsys.eplmp.core.services.IUserManagerLocal;
import org.polarsys.eplmp.server.auth.AuthConfig;
import org.polarsys.eplmp.server.auth.jwt.JWTokenFactory;
import org.polarsys.eplmp.server.rest.dto.AccountDTO;
import org.polarsys.eplmp.server.rest.dto.GCMAccountDTO;
import org.polarsys.eplmp.server.rest.dto.WorkspaceDTO;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@RequestScoped
@Path("accounts")
@Api(value = "accounts", description = "Operations about accounts")
@DeclareRoles({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
@RolesAllowed({UserGroupMapping.REGULAR_USER_ROLE_ID, UserGroupMapping.ADMIN_ROLE_ID})
public class AccountResource {

    @Inject
    private IAccountManagerLocal accountManager;
    @Inject
    private IUserManagerLocal userManager;
    @Inject
    private IContextManagerLocal contextManager;
    @Inject
    private IOAuthManagerLocal authManager;
    @Inject
    private AuthConfig authConfig;

    private static final Logger LOGGER = Logger.getLogger(AccountResource.class.getName());
    private Mapper mapper;

    public AccountResource() {
    }

    @PostConstruct
    public void init() {
        mapper = DozerBeanMapperSingletonWrapper.getInstance();
    }

    @GET
    @Path("/me")
    @ApiOperation(value = "Get authenticated user's account",
            response = AccountDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of AccountDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public AccountDTO getAccount()
            throws AccountNotFoundException {
        Account account = accountManager.getMyAccount();
        AccountDTO accountDTO = mapper.map(account, AccountDTO.class);
        accountDTO.setAdmin(contextManager.isCallerInRole(UserGroupMapping.ADMIN_ROLE_ID));
        accountDTO.setProviderId(authManager.getProviderId(account));
        return accountDTO;
    }

    @PUT
    @Path("/me")
    @ApiOperation(value = "Update user's account",
            response = AccountDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of updated AccountDTO"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateAccount(
            @ApiParam(hidden = true) @HeaderParam("Authorization") String authorizationString,
            @ApiParam(required = true, value = "Updated account") AccountDTO accountDTO)
            throws AccountNotFoundException, NotAllowedException {

        // If current password is specified, authenticate user with it
        String password = accountDTO.getPassword();
        if (password != null && !password.isEmpty()) {
            if (accountManager.authenticateAccount(contextManager.getCallerPrincipalLogin(), accountDTO.getPassword()) == null) {
                throw new NotAllowedException(new Locale(accountDTO.getLanguage()), "NotAllowedException68");
            }
        } else {
            if (authorizationString == null
                    || !authorizationString.startsWith("Bearer ")
                    || !JWTokenFactory.isJWTValidBefore(authConfig.getJWTKey(), 2 * 60, authorizationString.substring("Bearer ".length()))) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }

        Account account = accountManager.updateAccount(accountDTO.getName(), accountDTO.getEmail(), accountDTO.getLanguage(), accountDTO.getNewPassword(), accountDTO.getTimeZone());
        return Response.ok().entity(mapper.map(account, AccountDTO.class)).build();
    }

    @POST
    @Path("/create")
    @ApiOperation(value = "Create user's account",
            response = AccountDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of created AccountDTO. Response will contain authentication token."),
            @ApiResponse(code = 202, message = "Account creation successful, but not yet enabled"),
            @ApiResponse(code = 400, message = "Bad request, read response message for more details"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response createAccount(
            @Context HttpServletRequest request,
            @Context HttpServletResponse response,
            @ApiParam(required = true, value = "Account to create") AccountDTO accountDTO)
            throws AccountAlreadyExistsException, CreationException {
        Account account = accountManager.createAccount(accountDTO.getLogin(), accountDTO.getName(), accountDTO.getEmail(), accountDTO.getLanguage(), accountDTO.getNewPassword(), accountDTO.getTimeZone());

        HttpSession session = request.getSession();

        if (account.isEnabled()) {

            String login = account.getLogin();

            try {
                LOGGER.log(Level.INFO, "Authenticating response");
                request.authenticate(response);
            } catch (IOException | ServletException e) {
                LOGGER.log(Level.WARNING, "Request.authenticate failed", e);
                return Response.status(Response.Status.FORBIDDEN).entity(e.getMessage()).build();
            }

            session.setAttribute("login", login);
            session.setAttribute("groups", UserGroupMapping.REGULAR_USER_ROLE_ID);


            Response.ResponseBuilder responseBuilder = Response.ok()
                    .entity(mapper.map(account, AccountDTO.class));

            if (authConfig.isJwtEnabled()) {
                responseBuilder.header("jwt", JWTokenFactory.createAuthToken(authConfig.getJWTKey(), new UserGroupMapping(login, UserGroupMapping.REGULAR_USER_ROLE_ID)));
            }

            return responseBuilder
                    .build();

        } else {
            session.invalidate();
            return Response.status(Response.Status.ACCEPTED).build();
        }

    }

    @GET
    @Path("/workspaces")
    @ApiOperation(value = "Get workspaces where authenticated user is active",
            response = WorkspaceDTO.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful retrieval of Workspaces. It can be an empty list."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWorkspaces() {
        Workspace[] workspaces = userManager.getWorkspacesWhereCallerIsActive();

        List<WorkspaceDTO> workspaceDTOs = new ArrayList<>();
        for (Workspace workspace : workspaces) {
            workspaceDTOs.add(mapper.map(workspace, WorkspaceDTO.class));
        }

        return Response.ok(new GenericEntity<List<WorkspaceDTO>>((List<WorkspaceDTO>) workspaceDTOs) {
        }).build();
    }

    @PUT
    @Path("gcm")
    @ApiOperation(value = "Update GCM account for authenticated user",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful retrieval of created GCMAccount."),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setGCMAccount(
            @ApiParam(required = true, value = "GCM account to set") GCMAccountDTO data)
            throws EntityAlreadyExistsException, AccountNotFoundException, CreationException {
        accountManager.setGCMAccount(data.getGcmId());
        return Response.noContent().build();
    }


    @DELETE
    @Path("gcm")
    @ApiOperation(value = "Update GCM account for authenticated user",
            response = Response.class)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Successful delete of GCMAccount"),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Internal server error")
    })
    public Response deleteGCMAccount()
            throws EntityNotFoundException {
        accountManager.deleteGCMAccount();
        return Response.noContent().build();
    }

}

package org.wso2.carbon.automation.api.clients.registry;

import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.automation.api.clients.utils.AuthenticateStub;
import org.wso2.carbon.registry.relations.stub.AddAssociationRegistryExceptionException;
import org.wso2.carbon.registry.relations.stub.GetDependenciesRegistryExceptionException;
import org.wso2.carbon.registry.relations.stub.RelationAdminServiceStub;
import org.wso2.carbon.registry.relations.stub.beans.xsd.DependenciesBean;

import java.rmi.RemoteException;

public class RelationServiceClient {

    private static final Log log = LogFactory.getLog(RelationServiceClient.class);

    private RelationAdminServiceStub relationAdminServiceStub;
    private final String serviceName = "RelationAdminService";

    public RelationServiceClient(String backendURL, String sessionCookie) throws AxisFault {
        String endPoint = backendURL + serviceName;
        relationAdminServiceStub = new RelationAdminServiceStub(endPoint);
        AuthenticateStub.authenticateStub(sessionCookie, relationAdminServiceStub);
    }

    public RelationServiceClient(String backendURL, String userName, String password)
            throws AxisFault {
        String endPoint = backendURL + serviceName;
        relationAdminServiceStub = new RelationAdminServiceStub(endPoint);
        AuthenticateStub.authenticateStub(userName, password, relationAdminServiceStub);
    }

    public void addAssociation(String path, String type, String associationPath, String toDo)
            throws RemoteException, AddAssociationRegistryExceptionException {
        try {
            relationAdminServiceStub.addAssociation(path, type, associationPath, toDo);
        } catch (RemoteException e) {
            log.error("Add association error ");
            throw new RemoteException("Add association error ", e);
        } catch (AddAssociationRegistryExceptionException e) {
            log.error("Add association error ");
            throw new AddAssociationRegistryExceptionException("Add association error ", e);
        }
    }

    public DependenciesBean getDependencies(String path)
            throws RemoteException, AddAssociationRegistryExceptionException {
        DependenciesBean dependenciesBean = null;
        try {
            dependenciesBean = relationAdminServiceStub.getDependencies(path);
        } catch (RemoteException e) {
            log.error("Get dependencies error ");
            throw new RemoteException("Get dependencies error ", e);
        } catch (GetDependenciesRegistryExceptionException e) {
            log.error("Get dependencies error");
            throw new AddAssociationRegistryExceptionException("Get dependencies error ", e);
        }

        return dependenciesBean;
    }


}
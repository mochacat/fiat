package com.netflix.spinnaker.fiat.roles.google

import com.google.api.services.admin.directory.Directory
import com.google.api.client.googleapis.batch.BatchRequest;
import com.netflix.spinnaker.fiat.permissions.ExternalUser
import com.google.api.services.admin.directory.model.Group;
import com.google.api.services.admin.directory.model.Groups;
import spock.lang.Specification

class GoogleDirectoryUserRolesProviderSpec extends Specification {
    GoogleDirectoryUserRolesProvider.Config config = new GoogleDirectoryUserRolesProvider.Config()

    def "should read google groups"() {
        setup:
        config.domain = "test.com"
        def group = new Group()
        group.set("email", "test@test.com")
        group.set("name", "test")
        List<Group> groupList = new ArrayList<>()
        groupList.add(group)
        def groups = new Groups()
        groups.setGroups(groupList)

        GoogleDirectoryUserRolesProvider provider = new GoogleDirectoryUserRolesProvider() {
            @Override
            Groups getGroupsFromEmail(String email) {
                return groups
            }
        }

        provider.setProperty("config", config)

        when:
        def result1 = provider.loadRoles(externalUser("testuser"))

        then:
        result1.name.containsAll(["test"])
        result1.name.size() == 1

        when:
        config.roleSources = [GoogleDirectoryUserRolesProvider.Config.RoleSource.EMAIL, GoogleDirectoryUserRolesProvider.Config.RoleSource.NAME]
        def result2 = provider.loadRoles(externalUser("testuser"))

        then:
        result2.name.containsAll(["test@test.com", "test"])
        result2.name.size() == 2

        when:
        config.roleSources = [GoogleDirectoryUserRolesProvider.Config.RoleSource.EMAIL]
        def result3 = provider.loadRoles(externalUser("testuser"))

        then:
        result3.name.containsAll(["test@test.com"])
        result3.name.size() == 1

        when:
        //test that a null name does not break the resolution
        group.setName(null)
        groupList.clear()
        groupList.add(group)
        def result4 = provider.loadRoles(externalUser("testuser"))

        then:
        result4.name.containsAll(["test@test.com"])
        result4.name.size() == 1



    }

    def "should not call Google API to load role for managed service account"() {
        setup:
        config.domain = "test.com"
        def group = new Group()
        def userEmail = "0000-00-00-00-000000@managed-service-account"
        group.set("name", "test")
        group.set("email", userEmail)
        List<Group> groupList = new ArrayList<>()
        groupList.add(group)
        def groups = new Groups()
        groups.setGroups(groupList)

        GoogleDirectoryUserRolesProvider provider = new GoogleDirectoryUserRolesProvider() {
            @Override
            Groups getGroupsFromEmail(String email) {
                return groups
            }
        }

        provider.setProperty("config", config)

        when:
        def roles = provider.loadRoles(externalUser(userEmail))

        then:
        roles == new ArrayList()

    }

    def "should not call Google API to load multiple role for managed service accounts"() {
        setup:
        List<Group> groupList = new ArrayList<>()

        // TODO figure out where to return the groups
        config.domain = "test.com"
        def group1 = new Group()
        group1.set("name", "test")
        group1.set("email", "test@test.com")
        groupList.add(group1)

        def group2 = new Group()
        def userEmailManaged = "0000-00-00-00-000000@managed-service-account"
        group1.set("name", "managedUserGroup")
        group2.set("email", userEmailManaged)
        groupList.add(group2)

        def groups = new Groups()
        groups.setGroups(groupList)

        def service = Mock(Directory)
        // this doesn't seem to be mocked correctly since it tries to call the google api getRequestFactory D:
        service.batch(_) >> GroovyMock(BatchRequest)

        GoogleDirectoryUserRolesProvider provider = new GoogleDirectoryUserRolesProvider() {
            @Override
            Directory getDirectoryService() {
                return service
            }

            @Override
            Groups getGroupsFromEmail(String email) {
                return groups
            }
        }

        provider.setProperty("config", config)

        when:
        def results = provider.multiLoadRoles([externalUser("test@test.com"), externalUser("0000-00-00-00-000000@managed-service-account")])

        then:
        //
        results.size() == 1
        results.containsKey("test@test.com")
    }


    private static ExternalUser externalUser(String id) {
        return new ExternalUser().setId(id)
    }
}

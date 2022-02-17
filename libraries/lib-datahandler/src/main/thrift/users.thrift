/*
 * Copyright Siemens AG, 2014-2017. Part of the SW360 Portal Project.
 * With contributions by Bosch Software Innovations GmbH, 2016.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
include "sw360.thrift"
namespace java org.eclipse.sw360.datahandler.thrift.users
namespace php sw360.thrift.users

typedef sw360.RequestStatus RequestStatus
typedef sw360.RequestSummary RequestSummary
typedef sw360.PaginationData PaginationData

enum UserGroup {
    USER = 0,
    ADMIN = 1,
    CLEARING_ADMIN = 2,
    ECC_ADMIN = 3,
    SECURITY_ADMIN = 4,
    SW360_ADMIN = 5,
    CLEARING_EXPERT = 6
}

enum LocalGroup {
    BU = 0,
    CONTRIBUTOR = 1,
    MODERATOR = 2,
    OWNER = 3,
}

enum RequestedAction {
    READ = 1,
    WRITE = 2,
    DELETE = 3,
    USERS = 4,
    CLEARING = 5,
    ATTACHMENTS = 6,
    WRITE_ECC = 7,
}
struct User {

    1: optional string id,
    2: optional string revision,
    3: optional string type = "user",
    4: required string email,
    5: optional UserGroup userGroup,
    6: optional string externalid, 
    7: optional string fullname,
    8: optional string givenname, // firstname or given name of the person
    9: optional string lastname, // lastname or surname of the person
    10: required string department,
    11: optional bool wantsMailNotification,
    12: optional string commentMadeDuringModerationRequest,
    13: optional map<string, bool> notificationPreferences,
    14: optional set<string> formerEmailAddresses,
    20: optional list<RestApiToken> restApiTokens,
    21: optional map<string, bool> myProjectsPreferenceSelection,
    22: optional map<string, set<UserGroup>> secondaryDepartmentsAndRoles,
    23: optional list<string> primaryRoles,
    24: optional bool deactivated
}

struct RestApiToken {
    1: optional string token,
    2: optional string name,
    3: optional string createdOn
    4: optional i32 numberOfDaysValid,
    5: optional set<string> authorities,
}

service UserService {

    /**
     * returns SW360-user with given id
     **/
    User getUser(1:string id);

    /**
     * returns SW360-user with given email
     **/
    User getByEmail(1:string email);

    /**
     * returns SW360-user with given token
     **/
    User getByApiToken(1:string token);

    /**
     * searches for a SW360 user by email, or, if no such user is found, by externalId
     **/
    User getByEmailOrExternalId(1:string email, 2:string externalId);

    /**
     * get list of all SW360-users in database with name equal to parameter name
     **/
    list<User> searchUsers(1:string name);

    /**
     * get list of all SW360-users in database
     **/
    list<User> getAllUsers();

    /**
     * add SW360-user to database, user.email is used as id
     **/
    RequestStatus addUser(1: User user);

    /**
     * update SW360-user in database
     **/
    RequestStatus updateUser(1: User user);

    /**
     * delete user from database, only possible if adminUser has permissions
     **/
    RequestStatus deleteUser(1: User user, 2: User adminUser);

    /**
     * returns department of the SW360-user with id equal to email
     **/
    string getDepartmentByEmail(1:string email);

    /**
     * get list of users with pagination
     **/
    map<PaginationData, list<User>> getUsersWithPagination(1: User user, 2: PaginationData pageData);

    /**
     * search users in database
     **/
    list<User> refineSearch(1: string text, 2: map<string, set<string>> subQueryRestrictions);

    /**
     * get departments of all user
     **/
    set<string> getUserDepartments();

    /**
     * get email of all user
     **/
    set<string> getUserEmails();

    RequestSummary importFileToDB();

    RequestStatus importDepartmentSchedule();

    map<string, list<User>> getAllUserByDepartment();

    map<string, list<User>> searchUsersByDepartment(1:string departmentKey);

    list<string> getAllDepartment();

    list<string>  getAllEmailByDepartment(string departmentKey);

    list<string>  getAllEmailOtherDepartment(string departmentKey);

    string searchUsersByDepartmentToJson(1: string department)

     string getAllEmailOtherDepartmentToJson(1: string department)

    set<string> getListFileLog();

    map<string, list<string>> getAllMessageError();

    string getLastModifiedFileName();

    string getPathConfigDepartment();

    void writePathFolderConfig(1:string pathFolder);



     void updateDepartmentToListUserCheck(1: list<User> users, 2: string department)

     void deleteDepartmentByUser(1: User user, 2: string department)

     void deleteDepartmentByListUser(1: list<User> users,2: string department)

     list<User> getAllUserByListEmail(1: list<string> emails)

     list<User> getAllUsersByDepartment(1: string department)

     void deleteUserByDepartment(1: string department)

}
